package org.academy.internal.server.misaka;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaNAT;
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
     * Settle player MSk budgets once per second.
     * Integrated networks cut the pool by admin player percents; unintegrated networks still use
     * benevolent-set fairness. Results feed device windows — no direct CP grant.
     */
    public static void settleAndApply(MinecraftServer server) {
        if (server.getTickCount() % SETTLE_INTERVAL_TICKS != 0) {
            return;
        }

        var access = ServerMisakaComputeAccess.INSTANCE;
        access.closeAllWindows(server);

        var index = MisakaComputeIndex.get(server);
        index.rebuildIfDirty(server);

        float ratio = cpPerMsk(server);
        var chargeByUuid = MisakaComputeUsageTracker.chargeSnapshot();
        var networkUsageById = new HashMap<>(MisakaComputeUsageTracker.networkSnapshot());
        var playerByName = new HashMap<String, ServerPlayer>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getGameProfile().name();
            playerByName.put(name, player);
            index.putPlayerName(name, player.getUUID());
        }

        var activeDataEarly = MisakaActiveNetworkData.get(server);
        for (var entry : chargeByUuid.entrySet()) {
            float charge = entry.getValue() == null ? 0.0f : entry.getValue();
            if (!(charge > 0.0f)) {
                continue;
            }
            ServerPlayer owner = null;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getUUID().equals(entry.getKey())) {
                    owner = player;
                    break;
                }
            }
            if (owner == null) {
                continue;
            }
            var active = activeDataEarly.getActive(owner).orElse(null);
            if (active != null) {
                networkUsageById.merge(active, charge, Float::sum);
            }
        }

        var groupsByNetwork = new HashMap<UUID, List<MisakaComputeSettle.GroupBucket>>();
        var namesNeeded = new HashSet<String>();
        for (var entry : index.benevolentGroups().entrySet()) {
            var key = entry.getKey();
            namesNeeded.addAll(key.groupNames());
            groupsByNetwork
                    .computeIfAbsent(key.networkId(), ignored -> new ArrayList<>())
                    .add(new MisakaComputeSettle.GroupBucket(key.groupNames(), entry.getValue()));
        }

        var allocations = MisakaNetworkAllocations.get(server);
        var networkIds = new HashSet<UUID>();
        networkIds.addAll(groupsByNetwork.keySet());
        networkIds.addAll(index.networkTotals().keySet());

        for (UUID networkId : networkIds) {
            String recon = index.reconstructionPrivilege(networkId);
            if (recon != null && !recon.isEmpty()) {
                namesNeeded.add(recon);
                UUID privilegeUuid = index.resolvePlayerUuid(recon);
                if (privilegeUuid == null) {
                    ServerPlayer online = playerByName.get(recon);
                    if (online != null) {
                        privilegeUuid = online.getUUID();
                    }
                }
                if (privilegeUuid != null && index.reconstructionSisterUuid(networkId) != null) {
                    allocations.ensureDefaultPrivilege(networkId, privilegeUuid);
                }
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (access.hasDeviceConsumer(player)) {
                namesNeeded.add(player.getGameProfile().name());
            }
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

        var nextSatisfaction = new HashMap<String, Float>();
        var nextAllocated = new HashMap<String, Float>();
        var nextNetworkDemand = new HashMap<UUID, Float>();
        var nextNetworkPriority = new HashMap<UUID, Float>();
        var nextNetworkShared = new HashMap<UUID, Float>();
        var nextBudgets = new HashMap<UUID, Float>();
        var governance = MisakaNetworkGovernance.get(server);

        for (UUID networkId : networkIds.stream().sorted().toList()) {
            float networkBurn = networkUsageById.getOrDefault(networkId, 0.0f);
            var groupsForNet = applyNetworkBurn(
                    groupsByNetwork.getOrDefault(networkId, List.of()),
                    networkBurn
            );
            Map<String, Boolean> online = onlineByNetwork.getOrDefault(networkId, Map.of());
            float totalSupply = 0.0f;
            for (var bucket : groupsForNet) {
                if (bucket != null) {
                    totalSupply += Math.max(0.0f, bucket.mskTick());
                }
            }

            if (index.reconstructionSisterUuid(networkId) == null) {
                var demandForNet = new HashMap<String, Float>();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    String name = player.getGameProfile().name();
                    if (!networkId.equals(activeNetworkByName.get(name))) {
                        continue;
                    }
                    if (!access.hasDeviceConsumer(player)) {
                        continue;
                    }
                    demandForNet.put(name, Float.MAX_VALUE / 4.0f);
                }
                float uiDemand = demandForNet.isEmpty() ? 0.0f : totalSupply;
                nextNetworkDemand.put(networkId, uiDemand + Math.max(0.0f, networkBurn));
                var accessNames = sharedAccessNames(governance, networkId, demandForNet.keySet(), playerByName);
                // Device consumers on ActiveNetwork receive budgets even OOR (satellite / designator).
                // Coverage still gates sister supply via networkTotals; it must not zero device cuts.
                var settleOnline = new HashMap<>(online);
                for (String name : demandForNet.keySet()) {
                    settleOnline.put(name, true);
                }
                var partial = MisakaComputeSettle.settleUnintegrated(
                        groupsForNet,
                        demandForNet,
                        settleOnline,
                        null,
                        ratio,
                        accessNames
                );
                nextSatisfaction.putAll(partial.satisfactionByName());
                float prioritySum = 0.0f;
                for (var entry : partial.personalAllocatedMskByName().entrySet()) {
                    nextAllocated.merge(entry.getKey(), entry.getValue(), Float::sum);
                    prioritySum += entry.getValue();
                    ServerPlayer player = playerByName.get(entry.getKey());
                    if (player != null) {
                        nextBudgets.merge(player.getUUID(), entry.getValue(), Float::sum);
                    }
                }
                float sharedSum = 0.0f;
                for (var entry : partial.sharedAllocatedMskByName().entrySet()) {
                    nextAllocated.merge(entry.getKey(), entry.getValue(), Float::sum);
                    sharedSum += entry.getValue();
                    ServerPlayer player = playerByName.get(entry.getKey());
                    if (player != null) {
                        nextBudgets.merge(player.getUUID(), entry.getValue(), Float::sum);
                    }
                }
                nextNetworkPriority.put(networkId, prioritySum);
                nextNetworkShared.put(networkId, sharedSum);
            } else {
                var playerPercents = allocations.get(networkId);
                float demandSum = 0.0f;
                float prioritySum = 0.0f;
                for (var entry : playerPercents.entrySet()) {
                    UUID playerUuid = entry.getKey();
                    int percent = entry.getValue() == null ? 0 : entry.getValue();
                    if (percent <= 0) {
                        continue;
                    }
                    ServerPlayer player = null;
                    for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
                        if (onlinePlayer.getUUID().equals(playerUuid)) {
                            player = onlinePlayer;
                            break;
                        }
                    }
                    if (player == null) {
                        continue;
                    }
                    String name = player.getGameProfile().name();
                    if (!networkId.equals(activeNetworkByName.get(name))) {
                        continue;
                    }
                    // Coverage (online map) is not required: player UUID% is an admin cut of the
                    // network pool for device budgets (orbital designator may be used OOR).
                    if (!governance.hasPermissionOrOpen(
                            player, networkId, MisakaNetworkPermission.ACCESS, MisakaNetworkPermission.ADMIN
                    )) {
                        continue;
                    }
                    float budget = totalSupply * (percent / 100.0f);
                    if (!(budget > 0.0f)) {
                        continue;
                    }
                    nextBudgets.merge(playerUuid, budget, Float::sum);
                    nextAllocated.merge(name, budget, Float::sum);
                    nextSatisfaction.put(name, 1.0f);
                    demandSum += budget;
                    prioritySum += budget;
                }
                nextNetworkDemand.put(networkId, demandSum + Math.max(0.0f, networkBurn));
                nextNetworkPriority.put(networkId, prioritySum);
                nextNetworkShared.put(networkId, 0.0f);
            }
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

        access.clearBudgets();
        nextBudgets.forEach(access::setPlayerBudget);
        access.openAllWindows(server);

        MisakaComputeUsageTracker.clear();
        MisakaDeviceQuotaSync.syncAll(server);
    }

    /**
     * Immediately apply the device-budget cut for {@code networkId} to this player and reopen
     * their ACTUAL/FORCE window. Used when ActiveNetwork changes mid settle-window (e.g. laser
     * designator binds to a satellite network). Spending still goes only through
     * {@link org.academy.api.common.misaka.MisakaComputeAccess#consumeRate}.
     */
    public static void syncPlayerDeviceBudget(ServerPlayer player, UUID networkId) {
        if (player == null || networkId == null) {
            return;
        }
        var server = player.level().getServer();
        if (server == null) {
            return;
        }
        var access = ServerMisakaComputeAccess.INSTANCE;
        float budget = computeDeviceBudgetMsk(server, player, networkId);
        access.setPlayerBudget(player.getUUID(), budget);
        access.reopenWindow(player);
    }

    /**
     * Device-budget MSk/s for {@code player} on {@code networkId}, using the same rules as
     * {@link #settleAndApply} (integrated UUID% cut, or unintegrated Max-Min with OOR-eligible
     * device consumers). Returns 0 when the player is not a device consumer or lacks ACCESS.
     */
    static float computeDeviceBudgetMsk(MinecraftServer server, ServerPlayer player, UUID networkId) {
        if (server == null || player == null || networkId == null) {
            return 0.0f;
        }
        var access = ServerMisakaComputeAccess.INSTANCE;
        if (!access.hasDeviceConsumer(player)) {
            return 0.0f;
        }
        var governance = MisakaNetworkGovernance.get(server);
        if (!governance.hasPermissionOrOpen(
                player, networkId, MisakaNetworkPermission.ACCESS, MisakaNetworkPermission.ADMIN
        )) {
            return 0.0f;
        }
        var index = MisakaComputeIndex.get(server);
        index.rebuildIfDirty(server);
        float totalSupply = index.networkTotals().getOrDefault(networkId, 0.0f);
        if (!(totalSupply > DEMAND_EPS)) {
            return 0.0f;
        }
        if (index.reconstructionSisterUuid(networkId) != null) {
            int percent = MisakaNetworkAllocations.get(server).get(networkId)
                    .getOrDefault(player.getUUID(), 0);
            if (percent <= 0) {
                return 0.0f;
            }
            return totalSupply * (percent / 100.0f);
        }

        var groupsForNet = new ArrayList<MisakaComputeSettle.GroupBucket>();
        for (var entry : index.benevolentGroups().entrySet()) {
            if (!networkId.equals(entry.getKey().networkId())) {
                continue;
            }
            groupsForNet.add(new MisakaComputeSettle.GroupBucket(
                    entry.getKey().groupNames(),
                    entry.getValue() == null ? 0.0f : entry.getValue()
            ));
        }
        float networkBurn = MisakaComputeUsageTracker.networkSnapshot().getOrDefault(networkId, 0.0f);
        for (var entry : MisakaComputeUsageTracker.chargeSnapshot().entrySet()) {
            float charge = entry.getValue() == null ? 0.0f : entry.getValue();
            if (!(charge > 0.0f)) {
                continue;
            }
            for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
                if (!onlinePlayer.getUUID().equals(entry.getKey())) {
                    continue;
                }
                var active = MisakaActiveNetworkData.get(server).getActive(onlinePlayer).orElse(null);
                if (networkId.equals(active)) {
                    networkBurn += charge;
                }
                break;
            }
        }
        groupsForNet = new ArrayList<>(applyNetworkBurn(groupsForNet, networkBurn));

        var demandForNet = new HashMap<String, Float>();
        var settleOnline = new HashMap<String, Boolean>();
        var playerByName = new HashMap<String, ServerPlayer>();
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            String name = onlinePlayer.getGameProfile().name();
            playerByName.put(name, onlinePlayer);
            var active = MisakaActiveNetworkData.get(server).getActive(onlinePlayer).orElse(null);
            if (!networkId.equals(active) || !access.hasDeviceConsumer(onlinePlayer)) {
                continue;
            }
            demandForNet.put(name, Float.MAX_VALUE / 4.0f);
            settleOnline.put(name, true);
        }
        String selfName = player.getGameProfile().name();
        demandForNet.putIfAbsent(selfName, Float.MAX_VALUE / 4.0f);
        settleOnline.put(selfName, true);

        var accessNames = sharedAccessNames(governance, networkId, demandForNet.keySet(), playerByName);
        var partial = MisakaComputeSettle.settleUnintegrated(
                groupsForNet,
                demandForNet,
                settleOnline,
                null,
                cpPerMsk(server),
                accessNames
        );
        float personal = partial.personalAllocatedMskByName().getOrDefault(selfName, 0.0f);
        float shared = partial.sharedAllocatedMskByName().getOrDefault(selfName, 0.0f);
        return Math.max(0.0f, personal + shared);
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
     * Prefer the player's persisted ActiveNetwork when it still names a known network
     * (coverage is not required — orbital designator / device budgets must keep working OOR).
     * If unset, auto-select the first reachable network and persist it (no cooldown on auto-bind).
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
            if (networkIds.contains(id)) {
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

    /** Scale group supply so a direct network burn is removed before CP allocation. */
    private static List<MisakaComputeSettle.GroupBucket> applyNetworkBurn(
            List<MisakaComputeSettle.GroupBucket> groups,
            float burnMsk
    ) {
        if (groups == null || groups.isEmpty() || !(burnMsk > 0.0f)) {
            return groups == null ? List.of() : groups;
        }
        float total = 0.0f;
        for (var bucket : groups) {
            if (bucket != null) {
                total += Math.max(0.0f, bucket.mskTick());
            }
        }
        if (!(total > 0.0f)) {
            return groups;
        }
        float remain = Math.max(0.0f, total - burnMsk);
        float scale = remain / total;
        if (scale >= 1.0f) {
            return groups;
        }
        var scaled = new ArrayList<MisakaComputeSettle.GroupBucket>(groups.size());
        for (var bucket : groups) {
            if (bucket == null) {
                continue;
            }
            scaled.add(new MisakaComputeSettle.GroupBucket(bucket.groupNames(), bucket.mskTick() * scale));
        }
        return scaled;
    }

    /** Notify after Misaka contribution may have changed; also dirties the compute index. */
    public static void refreshCpForRecord(MinecraftServer server, MisakaSisterRecord record) {
        try {
            MisakaComputeIndex.get(server).markDirty();
            var names = new HashSet<String>();
            if (record.lastInteractedBenevolentPlayerName != null
                    && !record.lastInteractedBenevolentPlayerName.isEmpty()) {
                names.add(record.lastInteractedBenevolentPlayerName);
            }
            names.addAll(FavorService.benevolentNames(record));
            refreshCpForNames(server, names);
        } catch (RuntimeException ex) {
            AcademyCraft.LOGGER.error(
                    "Misaka CP refresh failed for sister {}",
                    record.misakaUuid,
                    ex
            );
        }
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
