package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dirty-rebuilt aggregate index for Misaka compute. Hot tick path must not scan the full roster.
 * Coverage edge flips adjust MSk totals incrementally without a full rebuild.
 */
public final class MisakaComputeIndex {
    public static final String UNASSIGNED = "";

    private static final MisakaComputeIndex INSTANCE = new MisakaComputeIndex();

    private boolean dirty = true;
    private boolean topologyDirty = true;
    private final Map<BlockPos, BlockPos> nodeToNetworkId = new HashMap<>();
    private final Map<BlockPos, Float> networkTotalMskPerSecond = new HashMap<>();
    private final Map<BlockPos, String> networkReconstructionPrivilege = new HashMap<>();
    private final Map<NetworkClosestKey, Float> groupMskPerSecond = new HashMap<>();
    private final Map<BlockPos, List<UUID>> networkSisterOrder = new HashMap<>();
    private final Map<String, UUID> playerNameToUuid = new HashMap<>();
    /** Last known in-coverage contribution flag (not persisted). */
    private final Map<UUID, Boolean> coverageContributing = new HashMap<>();

    private MisakaComputeIndex() {
    }

    public static MisakaComputeIndex get() {
        return INSTANCE;
    }

    public void markDirty() {
        dirty = true;
    }

    public void markTopologyDirty() {
        topologyDirty = true;
        dirty = true;
        nodeToNetworkId.clear();
        MisakaNetworkCoverage.invalidate();
    }

    public void putPlayerName(String name, UUID uuid) {
        if (name == null || name.isEmpty() || uuid == null) {
            return;
        }
        playerNameToUuid.put(name, uuid);
    }

    public void removePlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        playerNameToUuid.remove(name);
    }

    public @Nullable UUID resolvePlayerUuid(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return playerNameToUuid.get(name);
    }

    public void rebuildIfDirty(MinecraftServer server) {
        if (!dirty) {
            return;
        }
        rebuild(server);
        dirty = false;
        topologyDirty = false;
    }

    public BlockPos resolveNetworkIdCached(ServerLevel level, BlockPos nodePos) {
        if (nodePos == null) {
            return BlockPos.ZERO;
        }
        var immutable = nodePos.immutable();
        if (!topologyDirty) {
            var cached = nodeToNetworkId.get(immutable);
            if (cached != null) {
                return cached;
            }
        }
        var resolved = WirelessForwardingMisakaNAT.resolveNetworkIdRaw(level, immutable);
        nodeToNetworkId.put(immutable, resolved);
        return resolved;
    }

    public Map<BlockPos, Float> networkTotals() {
        return Map.copyOf(networkTotalMskPerSecond);
    }

    public Map<NetworkClosestKey, Float> closestGroups() {
        return Map.copyOf(groupMskPerSecond);
    }

    public @Nullable String reconstructionPrivilege(BlockPos networkId) {
        return networkId == null ? null : networkReconstructionPrivilege.get(networkId.immutable());
    }

    public int networkSisterCount(BlockPos networkId) {
        if (networkId == null) {
            return 0;
        }
        var list = networkSisterOrder.get(networkId.immutable());
        return list == null ? 0 : list.size();
    }

    public List<UUID> pageSisters(BlockPos networkId, int offset, int limit) {
        if (networkId == null || limit <= 0) {
            return List.of();
        }
        var list = networkSisterOrder.get(networkId.immutable());
        if (list == null || list.isEmpty() || offset >= list.size()) {
            return List.of();
        }
        int from = Math.max(0, offset);
        int to = Math.min(list.size(), from + limit);
        return List.copyOf(list.subList(from, to));
    }

    public @Nullable Boolean lastCoverageContributing(UUID misakaUuid) {
        return misakaUuid == null ? null : coverageContributing.get(misakaUuid);
    }

    /** Seed coverage flag without adjusting totals (rebuild already applied geometry). */
    public void seedCoverageContributing(UUID misakaUuid, boolean inCoverage) {
        if (misakaUuid != null) {
            coverageContributing.put(misakaUuid, inCoverage);
        }
    }

    /**
     * Incremental MSk adjust when a sister crosses coverage. Does not mark dirty / scan roster.
     */
    public void adjustCoverageContribution(
            MinecraftServer server,
            MisakaSisterRecord record,
            boolean wasIn,
            boolean nowIn
    ) {
        if (record == null || record.misakaUuid == null || wasIn == nowIn) {
            return;
        }
        coverageContributing.put(record.misakaUuid, nowIn);
        if (!record.awakened || record.networkNodePos == null || record.starving || dirty) {
            return;
        }
        var level = server.overworld();
        var networkId = resolveNetworkIdCached(level, record.networkNodePos);
        float msk = MisakaComputeContribution.mskPerSecond(record.perception);
        float delta = nowIn ? msk : -msk;
        applyMskDelta(networkId, closestName(record), delta);
    }

    private void applyMskDelta(BlockPos networkId, String closest, float delta) {
        if (delta == 0.0f) {
            return;
        }
        networkTotalMskPerSecond.merge(networkId, delta, Float::sum);
        float total = networkTotalMskPerSecond.getOrDefault(networkId, 0.0f);
        if (total <= 0.0f) {
            networkTotalMskPerSecond.remove(networkId);
        }
        var key = new NetworkClosestKey(networkId, closest);
        groupMskPerSecond.merge(key, delta, Float::sum);
        float group = groupMskPerSecond.getOrDefault(key, 0.0f);
        if (group <= 0.0f) {
            groupMskPerSecond.remove(key);
        }
    }

    /** Test hook: apply MSk delta without a live server. */
    void testingApplyMskDelta(BlockPos networkId, String closest, float delta) {
        applyMskDelta(networkId, closest == null ? UNASSIGNED : closest, delta);
    }

    /** Test hook: clear aggregate maps without touching roster topology. */
    void testingClearAggregates() {
        networkTotalMskPerSecond.clear();
        groupMskPerSecond.clear();
        coverageContributing.clear();
    }

    boolean testingIsDirty() {
        return dirty;
    }

    void testingSetDirty(boolean value) {
        dirty = value;
    }

    private static String closestName(MisakaSisterRecord record) {
        String closest = record.lastInteractedBenevolentPlayerName == null
                ? UNASSIGNED
                : record.lastInteractedBenevolentPlayerName;
        if (closest.isEmpty() || !FavorService.isPrivilegePlayer(record, closest)) {
            return UNASSIGNED;
        }
        return closest;
    }

    private void rebuild(MinecraftServer server) {
        nodeToNetworkId.clear();
        networkTotalMskPerSecond.clear();
        networkReconstructionPrivilege.clear();
        groupMskPerSecond.clear();
        networkSisterOrder.clear();
        coverageContributing.clear();

        var level = server.overworld();
        var roster = MisakaSisterRoster.get(server);
        record SisterSort(
                UUID uuid,
                int serial,
                BlockPos networkId,
                float msk,
                String closest,
                boolean reconstruction,
                boolean starving,
                boolean inCoverage
        ) {
        }
        var sorted = new ArrayList<SisterSort>();

        for (var record : roster.all()) {
            // Bound awakened sisters stay on the manage list / recon pick even while starving.
            // Only non-starving in-coverage sisters contribute MSk.
            if (!record.awakened || record.networkNodePos == null) {
                continue;
            }
            var networkId = resolveDuringRebuild(level, record.networkNodePos);
            float msk = MisakaComputeContribution.mskPerSecond(record.perception);
            String closest = closestName(record);
            boolean reconstruction = record.perception >= 101;
            var loadedSister = MisakaNetworkCoverage.findLoadedSister(server, record.misakaUuid);
            var sampleLevel = MisakaNetworkCoverage.sampleLevel(server, record, loadedSister);
            BlockPos sample = MisakaNetworkCoverage.samplePos(record, loadedSister);
            boolean inCoverage = MisakaNetworkCoverage.canUseMisakaService(sampleLevel, networkId, sample);
            coverageContributing.put(record.misakaUuid, inCoverage);
            sorted.add(new SisterSort(
                    record.misakaUuid,
                    record.serial,
                    networkId,
                    msk,
                    closest,
                    reconstruction,
                    record.starving,
                    inCoverage
            ));
        }

        sorted.sort(Comparator.comparingInt(SisterSort::serial));
        var reconCandidates = new HashMap<BlockPos, SisterSort>();

        for (var sister : sorted) {
            networkSisterOrder
                    .computeIfAbsent(sister.networkId, ignored -> new ArrayList<>())
                    .add(sister.uuid);
            if (sister.reconstruction) {
                reconCandidates.putIfAbsent(sister.networkId, sister);
            }
            if (sister.starving || !sister.inCoverage) {
                continue;
            }
            networkTotalMskPerSecond.merge(sister.networkId, sister.msk, Float::sum);
            groupMskPerSecond.merge(
                    new NetworkClosestKey(sister.networkId, sister.closest),
                    sister.msk,
                    Float::sum
            );
        }

        for (var entry : reconCandidates.entrySet()) {
            networkReconstructionPrivilege.put(entry.getKey(), entry.getValue().closest());
        }
    }

    private BlockPos resolveDuringRebuild(ServerLevel level, BlockPos nodePos) {
        var immutable = nodePos.immutable();
        var cached = nodeToNetworkId.get(immutable);
        if (cached != null && !topologyDirty) {
            return cached;
        }
        // During full rebuild after topology dirty, always resolve once per unique node.
        cached = nodeToNetworkId.get(immutable);
        if (cached != null) {
            return cached;
        }
        var resolved = WirelessForwardingMisakaNAT.resolveNetworkIdRaw(level, immutable);
        nodeToNetworkId.put(immutable, resolved);
        return resolved;
    }

    public record NetworkClosestKey(BlockPos networkId, String closestName) {
        public NetworkClosestKey {
            networkId = networkId == null ? BlockPos.ZERO : networkId.immutable();
            closestName = closestName == null ? UNASSIGNED : closestName;
        }
    }
}
