package org.academy.internal.server.misaka;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.misaka.MisakaNetworkPermission;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaNetworkAllocations;
import org.academy.internal.server.world.level.storage.MisakaNetworkGovernance;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MisakaComputeContribution {
    /** Default CP per 1 MSk (1 MSk : 2 CP). Overridden by GenericConfig.misakaCpPerMsk. */
    public static final float CP_PER_MSK = 2.0f;

    /** Settle once per second (20 ticks) using full MSk/s amounts. */
    public static final int SETTLE_INTERVAL_TICKS = 20;

    private static final float DEMAND_EPS = 1.0e-5f;

    /** Last settle personal satisfaction (allocated/demand) by player name. */
    public static final ConcurrentHashMap<String, Float> lastSatisfactionByName = new ConcurrentHashMap<>();

    /** Last settle allocated MSk (personal + shared) by player name. */
    public static final ConcurrentHashMap<String, Float> lastAllocatedMskByName = new ConcurrentHashMap<>();

    /** Last settle total demand MSk by network id. */
    public static final ConcurrentHashMap<UUID, Float> lastNetworkDemand = new ConcurrentHashMap<>();

    /** Last settle priority-pool allocated MSk sum by network id. */
    public static final ConcurrentHashMap<UUID, Float> lastNetworkPriorityAllocated = new ConcurrentHashMap<>();

    /** Last settle shared-pool allocated MSk sum by network id. */
    public static final ConcurrentHashMap<UUID, Float> lastNetworkSharedAllocated = new ConcurrentHashMap<>();

    private MisakaComputeContribution() {
    }

    public static float cpPerMsk(MinecraftServer server) {
        var academy = server.getAcademyCraftServer();
        if (academy == null) {
            return CP_PER_MSK;
        }
        return academy.getGenericConfig().misakaCpPerMsk;
    }

    public static float mskPerSecond(int perception) {
        if (perception <= 100) {
            return perception;
        }
        if (perception <= 110) {
            return 100f + 2f * (perception - 100);
        }
        return 120f + (280f / 90f) * (perception - 110);
    }

    /**
     * Settle personal + shared + leftover-sink CP once per second.
     * Each player receives from at most one network: their {@link MisakaActiveNetworkData}
     * selection if set and reachable, otherwise the lexicographically first reachable network
     * (auto-bind on first settle).
     */
    public static void settleAndApply(MinecraftServer server) {
        if (server.getTickCount() % SETTLE_INTERVAL_TICKS != 0) {
            return;
        }

        var index = MisakaComputeIndex.get(server);
        index.rebuildIfDirty(server);

        float ratio = cpPerMsk(server);
        var usageByUuid = MisakaComputeUsageTracker.snapshot();
        var usageByName = new HashMap<String, Float>();
        var playerByName = new HashMap<String, ServerPlayer>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getGameProfile().name();
            playerByName.put(name, player);
            index.putPlayerName(name, player.getUUID());
            float used = usageByUuid.getOrDefault(player.getUUID(), 0.0f);
            if (used > 0.0f) {
                usageByName.put(name, used);
            }
        }

        var groupsByNetwork = new HashMap<UUID, List<MisakaComputeSettle.GroupBucket>>();
        var namesNeeded = new HashSet<String>(usageByName.keySet());
        for (var entry : index.benevolentGroups().entrySet()) {
            var key = entry.getKey();
            List<String> names = key.groupNames();
            namesNeeded.addAll(names);
            groupsByNetwork
                    .computeIfAbsent(key.networkId(), ignored -> new ArrayList<>())
                    .add(new MisakaComputeSettle.GroupBucket(names, entry.getValue()));
        }

        var allocations = MisakaNetworkAllocations.get(server);
        var networkIds = new HashSet<UUID>();
        networkIds.addAll(groupsByNetwork.keySet());
        networkIds.addAll(index.networkTotals().keySet());

        var reconByNetwork = new HashMap<UUID, String>();
        var percentsByNetwork = new HashMap<UUID, int[]>();
        for (UUID networkId : networkIds) {
            String recon = index.reconstructionPrivilege(networkId);
            if (recon != null && !recon.isEmpty()) {
                reconByNetwork.put(networkId, recon);
                namesNeeded.add(recon);
            }
            percentsByNetwork.put(networkId, allocations.get(networkId));
        }

        var onlineByNetwork = new HashMap<UUID, Map<String, Boolean>>();
        for (UUID networkId : networkIds) {
            onlineByNetwork.put(networkId, reachableMap(networkId, namesNeeded, playerByName));
        }

        var activeData = MisakaActiveNetworkData.get(server);
        long gameTime = server.overworld().getGameTime();
        var activeNetworkByName = new HashMap<String, UUID>();
        for (String name : namesNeeded) {
            ServerPlayer player = playerByName.get(name);
            if (player == null) {
                continue;
            }
            UUID chosen = resolveActiveNetwork(
                    activeData,
                    player,
                    networkIds,
                    onlineByNetwork,
                    gameTime
            );
            if (chosen != null) {
                activeNetworkByName.put(name, chosen);
            }
        }

        var personalCp = new HashMap<String, Float>();
        var sharedCp = new HashMap<String, Float>();
        var networkCp = new HashMap<String, Float>();
        var nextSatisfaction = new HashMap<String, Float>();
        var nextAllocated = new HashMap<String, Float>();
        var nextNetworkDemand = new HashMap<UUID, Float>();
        var nextNetworkPriority = new HashMap<UUID, Float>();
        var nextNetworkShared = new HashMap<UUID, Float>();
        var governance = MisakaNetworkGovernance.get(server);

        for (UUID networkId : networkIds.stream().sorted().toList()) {
            var demandForNet = new HashMap<String, Float>();
            float demandSum = 0.0f;
            for (var entry : usageByName.entrySet()) {
                if (networkId.equals(activeNetworkByName.get(entry.getKey()))) {
                    demandForNet.put(entry.getKey(), entry.getValue());
                    demandSum += entry.getValue();
                }
            }
            nextNetworkDemand.put(networkId, demandSum);
            Map<String, Boolean> online = onlineByNetwork.getOrDefault(networkId, Map.of());
            var accessNames = sharedAccessNames(governance, networkId, demandForNet.keySet(), playerByName);
            var groupsForNet = groupsByNetwork.getOrDefault(networkId, List.of());
            // §7.1: a network without a reconstruction work stays dispersed — each benevolent
            // set keeps its own sisters' output instead of feeding one network-wide pool.
            var partial = index.reconstructionSisterUuid(networkId) == null
                    ? MisakaComputeSettle.settleUnintegrated(
                            groupsForNet,
                            demandForNet,
                            online,
                            null,
                            ratio,
                            accessNames
                    )
                    : MisakaComputeSettle.settle(
                            groupsForNet,
                            demandForNet,
                            online,
                            null,
                            percentsByNetwork.get(networkId),
                            reconByNetwork.get(networkId),
                            ratio,
                            accessNames
                    );
            mergeFloatMap(personalCp, partial.personalCpByName());
            mergeFloatMap(sharedCp, partial.sharedCpByName());
            mergeFloatMap(networkCp, partial.networkCpByName());
            nextSatisfaction.putAll(partial.satisfactionByName());
            float prioritySum = 0.0f;
            for (var entry : partial.personalAllocatedMskByName().entrySet()) {
                nextAllocated.merge(entry.getKey(), entry.getValue(), Float::sum);
                prioritySum += entry.getValue();
            }
            float sharedSum = 0.0f;
            for (var entry : partial.sharedAllocatedMskByName().entrySet()) {
                nextAllocated.merge(entry.getKey(), entry.getValue(), Float::sum);
                sharedSum += entry.getValue();
            }
            nextNetworkPriority.put(networkId, prioritySum);
            nextNetworkShared.put(networkId, sharedSum);
        }

        lastSatisfactionByName.clear();
        lastSatisfactionByName.putAll(nextSatisfaction);
        lastAllocatedMskByName.clear();
        lastAllocatedMskByName.putAll(nextAllocated);
        lastNetworkDemand.clear();
        lastNetworkDemand.putAll(nextNetworkDemand);
        lastNetworkPriorityAllocated.clear();
        lastNetworkPriorityAllocated.putAll(nextNetworkPriority);
        lastNetworkSharedAllocated.clear();
        lastNetworkSharedAllocated.putAll(nextNetworkShared);

        var academy = server.getAcademyCraftServer();
        if (academy != null) {
            var ability = academy.getAbilitySystemServer();
            applyNamedCp(personalCp, playerByName, ability);
            applyNamedCp(sharedCp, playerByName, ability);
            applyNamedCp(networkCp, playerByName, ability);
        }

        MisakaComputeUsageTracker.clear();
    }

    /** Cached personal satisfaction from the last settle, or empty if never settled. */
    public static float lastSatisfaction(String name) {
        if (name == null || name.isEmpty()) {
            return 0.0f;
        }
        return lastSatisfactionByName.getOrDefault(name, 0.0f);
    }

    /** Cached allocated MSk from the last settle for {@code name}. */
    public static float lastAllocatedMsk(String name) {
        if (name == null || name.isEmpty()) {
            return 0.0f;
        }
        return lastAllocatedMskByName.getOrDefault(name, 0.0f);
    }

    /**
     * Network demand: prefer last settle cache; otherwise sum live usage for players
     * whose active network is {@code networkId}.
     */
    public static float networkDemandMsk(MinecraftServer server, UUID networkId) {
        if (networkId == null) {
            return 0.0f;
        }
        Float cached = lastNetworkDemand.get(networkId);
        if (cached != null) {
            return cached;
        }
        if (server == null) {
            return 0.0f;
        }
        var usageByUuid = MisakaComputeUsageTracker.snapshot();
        if (usageByUuid.isEmpty()) {
            return 0.0f;
        }
        var activeData = MisakaActiveNetworkData.get(server);
        float sum = 0.0f;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            float used = usageByUuid.getOrDefault(player.getUUID(), 0.0f);
            if (!(used > 0.0f)) {
                continue;
            }
            Optional<UUID> active = activeData.getActive(player);
            if (active.isPresent() && networkId.equals(active.get())) {
                sum += used;
            }
        }
        return sum;
    }

    /** Network-level satisfaction = min(1, supply / max(demand, eps)). */
    public static float networkSatisfaction(float supplyMsk, float demandMsk) {
        if (!(demandMsk > DEMAND_EPS)) {
            return 1.0f;
        }
        return Math.min(1.0f, supplyMsk / demandMsk);
    }

    public static float lastNetworkPriorityAllocatedMsk(UUID networkId) {
        if (networkId == null) {
            return 0.0f;
        }
        return lastNetworkPriorityAllocated.getOrDefault(networkId, 0.0f);
    }

    public static float lastNetworkSharedAllocatedMsk(UUID networkId) {
        if (networkId == null) {
            return 0.0f;
        }
        return lastNetworkSharedAllocated.getOrDefault(networkId, 0.0f);
    }

    /**
     * Shared-pool ACCESS holders. Before first integration (empty admin/member lists),
     * all demanders are treated as ACCESS so early networks still settle.
     */
    private static Set<String> sharedAccessNames(
            MisakaNetworkGovernance governance,
            UUID networkId,
            Set<String> demandNames,
            Map<String, ServerPlayer> playerByName
    ) {
        var access = new HashSet<String>();
        if (governance == null || networkId == null || demandNames == null) {
            return access;
        }
        for (String name : demandNames) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (governance.hasPermissionOrOpen(
                    playerByName.get(name),
                    networkId,
                    MisakaNetworkPermission.ACCESS
            )) {
                access.add(name);
            }
        }
        return access;
    }

    /**
     * Prefer persisted ActiveNetwork when reachable; otherwise auto-select the first reachable
     * network and persist it (no cooldown on auto-bind).
     */
    private static @Nullable UUID resolveActiveNetwork(
            MisakaActiveNetworkData activeData,
            ServerPlayer player,
            HashSet<UUID> networkIds,
            Map<UUID, Map<String, Boolean>> onlineByNetwork,
            long gameTime
    ) {
        String name = player.getGameProfile().name();
        Optional<UUID> stored = activeData.getActive(player);
        if (stored.isPresent()) {
            UUID id = stored.get();
            if (Boolean.TRUE.equals(onlineByNetwork.getOrDefault(id, Map.of()).get(name))) {
                return id;
            }
        }
        UUID fallback = null;
        for (UUID networkId : networkIds.stream().sorted().toList()) {
            if (Boolean.TRUE.equals(onlineByNetwork.getOrDefault(networkId, Map.of()).get(name))) {
                fallback = networkId;
                break;
            }
        }
        if (fallback != null && stored.isEmpty()) {
            activeData.trySetActive(player, fallback, gameTime);
        }
        return fallback;
    }

    private static Map<String, Boolean> reachableMap(
            UUID networkId,
            HashSet<String> namesNeeded,
            Map<String, ServerPlayer> playerByName
    ) {
        var reachable = new HashMap<String, Boolean>();
        for (String name : namesNeeded) {
            ServerPlayer player = playerByName.get(name);
            if (player == null) {
                reachable.put(name, false);
                continue;
            }
            var level = (net.minecraft.server.level.ServerLevel) player.level();
            boolean ok = MisakaNAT.get().canUseMisakaService(level, networkId, player.blockPosition());
            reachable.put(name, ok);
        }
        return reachable;
    }

    private static void mergeFloatMap(Map<String, Float> target, Map<String, Float> source) {
        if (source == null) {
            return;
        }
        for (var entry : source.entrySet()) {
            float add = entry.getValue() == null ? 0.0f : entry.getValue();
            if (add != 0.0f) {
                target.merge(entry.getKey(), add, Float::sum);
            }
        }
    }

    private static void applyNamedCp(
            Map<String, Float> deltas,
            Map<String, ServerPlayer> players,
            AbilitySystemServer ability
    ) {
        for (var entry : deltas.entrySet()) {
            float add = entry.getValue() == null ? 0.0f : entry.getValue();
            if (!(add > 0.0f)) {
                continue;
            }
            ServerPlayer player = players.get(entry.getKey());
            if (player == null) {
                continue;
            }
            UUID uuid = player.getUUID();
            float max = ability.getPlayerMaxCP(uuid);
            float available = ability.getPlayerAvailableCP(uuid);
            float next = Math.min(max, available + add);
            if (next > available) {
                ability.setPlayerAvailableCP(uuid, next);
                ability.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
            }
        }
    }

    /** Notify after Misaka contribution may have changed; also dirties the compute index. */
    public static void refreshCpForRecord(MinecraftServer server, MisakaSisterRecord record) {
        MisakaComputeIndex.get(server).markDirty();
        var names = new HashSet<String>();
        if (record.lastInteractedBenevolentPlayerName != null
                && !record.lastInteractedBenevolentPlayerName.isEmpty()) {
            names.add(record.lastInteractedBenevolentPlayerName);
        }
        names.addAll(FavorService.benevolentNames(record));
        refreshCpForNames(server, names);
    }

    public static void refreshCpForNames(MinecraftServer server, Iterable<String> names) {
        var academy = server.getAcademyCraftServer();
        if (academy == null) {
            return;
        }
        var ability = academy.getAbilitySystemServer();
        for (String name : names) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            ServerPlayer player = MisakaPlayers.findOnlineByName(server, name);
            if (player != null) {
                ability.refreshPlayerCommonSkillBonuses(player.getUUID());
            }
        }
    }
}
