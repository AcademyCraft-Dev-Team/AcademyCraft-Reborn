package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.server.world.level.storage.MisakaNetworkAllocations;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public final class MisakaComputeContribution {
    /** Default CP per 1 MSk (1 MSk : 2 CP). Overridden by GenericConfig.misakaCpPerMsk. */
    public static final float CP_PER_MSK = 2.0f;

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

    /** Settle personal + network-pool CP for this server tick. */
    public static void settleAndApply(MinecraftServer server) {
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

        var buckets = new ArrayList<MisakaComputeSettle.ClosestBucket>();
        for (var entry : index.closestGroups().entrySet()) {
            var key = entry.getKey();
            String networkKey = Long.toString(key.networkId().asLong());
            buckets.add(new MisakaComputeSettle.ClosestBucket(
                    networkKey,
                    key.closestName(),
                    entry.getValue() / 20.0f
            ));
        }
        buckets.sort(Comparator
                .comparing(MisakaComputeSettle.ClosestBucket::networkKey)
                .thenComparing(MisakaComputeSettle.ClosestBucket::closestName));

        var allocations = MisakaNetworkAllocations.get(server);
        var networks = new ArrayList<MisakaComputeSettle.NetworkPoolInput>();
        for (var entry : index.networkTotals().entrySet()) {
            BlockPos networkId = entry.getKey();
            String networkKey = Long.toString(networkId.asLong());
            networks.add(new MisakaComputeSettle.NetworkPoolInput(
                    networkKey,
                    0.0f,
                    allocations.get(networkId),
                    index.reconstructionPrivilege(networkId)
            ));
        }
        networks.sort(Comparator.comparing(MisakaComputeSettle.NetworkPoolInput::networkKey));

        // Per-network reachable online: player must be online AND in that network's coverage.
        var onlineByNetwork = new HashMap<String, Map<String, Boolean>>();
        var namesNeeded = new HashSet<String>();
        for (var bucket : buckets) {
            if (bucket.closestName() != null && !bucket.closestName().isEmpty()) {
                namesNeeded.add(bucket.closestName());
            }
        }
        for (var network : networks) {
            if (network.reconstructionPrivilegeName() != null
                    && !network.reconstructionPrivilegeName().isEmpty()) {
                namesNeeded.add(network.reconstructionPrivilegeName());
            }
        }
        for (var bucket : buckets) {
            ensureReachableMap(onlineByNetwork, bucket.networkKey(), namesNeeded, playerByName);
        }
        for (var network : networks) {
            ensureReachableMap(onlineByNetwork, network.networkKey(), namesNeeded, playerByName);
        }

        // Settle one network at a time with that network's reachable online map so
        // MisakaComputeSettle's shared onlineByName still works correctly per call.
        // Buckets/networks for different keys must not share a single online map when
        // reachability differs — settle expects one onlineByName for the whole call.
        // Build a synthetic online map: name is online only if reachable for EVERY network
        // that mentions them — wrong. Instead merge settle per network.

        var personalCp = new HashMap<String, Float>();
        var networkCp = new HashMap<String, Float>();
        var poolMsk = new HashMap<String, Float>();

        var networkKeys = new HashSet<String>();
        for (var bucket : buckets) {
            networkKeys.add(bucket.networkKey());
        }
        for (var network : networks) {
            networkKeys.add(network.networkKey());
        }

        for (String networkKey : networkKeys.stream().sorted().toList()) {
            var netBuckets = new ArrayList<MisakaComputeSettle.ClosestBucket>();
            for (var bucket : buckets) {
                if (networkKey.equals(bucket.networkKey())) {
                    netBuckets.add(bucket);
                }
            }
            var netInputs = new ArrayList<MisakaComputeSettle.NetworkPoolInput>();
            for (var network : networks) {
                if (networkKey.equals(network.networkKey())) {
                    netInputs.add(network);
                }
            }
            Map<String, Boolean> online = onlineByNetwork.getOrDefault(networkKey, Map.of());
            var partial = MisakaComputeSettle.settle(netBuckets, usageByName, netInputs, online, ratio);
            // Usage must decrement across networks — settle mutates via remainingUsage internally
            // but we pass the same usageByName each time. Fix: track remaining usage across nets.
            mergeFloatMap(personalCp, partial.personalCpByName());
            mergeFloatMap(networkCp, partial.networkCpByName());
            mergeFloatMap(poolMsk, partial.poolMskByNetwork());
            // Reduce usageByName by personal MSk consumed this network
            for (var entry : partial.personalCpByName().entrySet()) {
                float cp = entry.getValue() == null ? 0.0f : entry.getValue();
                if (!(cp > 0.0f) || !(ratio > 0.0f)) {
                    continue;
                }
                float usedMsk = cp / ratio;
                usageByName.merge(entry.getKey(), -usedMsk, Float::sum);
                float left = usageByName.getOrDefault(entry.getKey(), 0.0f);
                if (left <= 0.0f) {
                    usageByName.remove(entry.getKey());
                }
            }
        }

        var academy = server.getAcademyCraftServer();
        if (academy != null) {
            var ability = academy.getAbilitySystemServer();
            applyNamedCp(personalCp, playerByName, ability);
            applyNamedCp(networkCp, playerByName, ability);
        }

        MisakaComputeUsageTracker.clear();
    }

    private static void ensureReachableMap(
            Map<String, Map<String, Boolean>> onlineByNetwork,
            String networkKey,
            HashSet<String> namesNeeded,
            Map<String, ServerPlayer> playerByName
    ) {
        if (networkKey == null || onlineByNetwork.containsKey(networkKey)) {
            return;
        }
        BlockPos networkId;
        try {
            networkId = BlockPos.of(Long.parseLong(networkKey));
        } catch (NumberFormatException ex) {
            onlineByNetwork.put(networkKey, Map.of());
            return;
        }
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
        onlineByNetwork.put(networkKey, reachable);
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
        int maxFavor = record.favorByPlayerName.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxFavor > 0) {
            for (var entry : record.favorByPlayerName.entrySet()) {
                if (entry.getValue() == maxFavor) {
                    names.add(entry.getKey());
                }
            }
        }
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
            ServerPlayer player = findOnlineByName(server, name);
            if (player != null) {
                ability.refreshPlayerCommonSkillBonuses(player.getUUID());
            }
        }
    }

    private static @Nullable ServerPlayer findOnlineByName(MinecraftServer server, String name) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().name().equals(name)) {
                return player;
            }
        }
        return null;
    }
}
