package org.academy.internal.server.misaka;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaNetworkRegistry;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dirty-rebuilt aggregate index for Misaka compute. Hot tick path must not scan the full roster.
 * Coverage edge flips adjust MSk totals incrementally without a full rebuild.
 * Aggregates are not persisted; SavedData exists only for per-server lifetime scoping.
 */
public final class MisakaComputeIndex extends SavedData {
    public static final String UNASSIGNED = "";
    /** Separator for multi-name benevolent group keys (must not appear in player names). */
    public static final String GROUP_KEY_SEP = "\u0001";

    /** Test-only override; when non-null, {@link #get(MinecraftServer)} returns it. */
    public static final AtomicReference<@Nullable MisakaComputeIndex> TESTING_OVERRIDE = new AtomicReference<>();

    /** Always creates an empty dirty index; aggregates are never persisted. */
    public static final Codec<MisakaComputeIndex> CODEC = Codec.INT.xmap(
            ignored -> new MisakaComputeIndex(),
            index -> 0
    );

    public static final SavedDataType<MisakaComputeIndex> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("misaka_compute_index"),
            MisakaComputeIndex::new,
            CODEC
    );

    private boolean dirty;
    private boolean topologyDirty;
    private final Map<BlockPos, UUID> nodeToNetworkId = new HashMap<>();
    private final Map<UUID, Float> networkTotalMskPerSecond = new HashMap<>();
    private final Map<UUID, String> networkReconstructionPrivilege = new HashMap<>();
    /** Lowest-serial reconstruction sister UUID per network (for O(1) hasReconstructionWork). */
    private final Map<UUID, UUID> networkReconstructionSister = new HashMap<>();
    private final Map<NetworkGroupKey, Float> groupMskPerSecond = new HashMap<>();
    private final Map<UUID, List<UUID>> networkSisterOrder = new HashMap<>();
    private final Map<String, UUID> playerNameToUuid = new HashMap<>();
    /** Last known in-coverage contribution flag (not persisted). */
    private final Map<UUID, Boolean> coverageContributing = new HashMap<>();

    public MisakaComputeIndex() {
        dirty = true;
        topologyDirty = true;
    }

    /** Test-only: install a stub index. Pass null to clear. */
    public static void testingInstall(@Nullable MisakaComputeIndex index) {
        TESTING_OVERRIDE.set(index);
    }

    public static MisakaComputeIndex get(MinecraftServer server) {
        var override = TESTING_OVERRIDE.get();
        if (override != null) {
            return override;
        }
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
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

    public UUID resolveNetworkIdCached(ServerLevel level, BlockPos nodePos) {
        if (nodePos == null) {
            return new UUID(0L, 0L);
        }
        var immutable = nodePos.immutable();
        if (!topologyDirty) {
            var cached = nodeToNetworkId.get(immutable);
            if (cached != null) {
                return cached;
            }
        }
        var server = level != null ? level.getServer() : null;
        var topologyLevel = server != null ? server.overworld() : level;
        UUID resolved;
        if (server != null) {
            var registry = MisakaNetworkRegistry.get(server);
            if (topologyDirty) {
                registry.reconcileTopology(topologyLevel);
            }
            resolved = registry.resolveOrCreate(topologyLevel, immutable);
        } else {
            resolved = WirelessForwardingMisakaNAT.resolveNetworkIdRaw(topologyLevel, immutable);
        }
        nodeToNetworkId.put(immutable, resolved);
        return resolved;
    }

    public Map<UUID, Float> networkTotals() {
        return Map.copyOf(networkTotalMskPerSecond);
    }

    public Map<NetworkGroupKey, Float> benevolentGroups() {
        return Map.copyOf(groupMskPerSecond);
    }

    public @Nullable String reconstructionPrivilege(UUID networkId) {
        return networkId == null ? null : networkReconstructionPrivilege.get(networkId);
    }

    public @Nullable UUID reconstructionSisterUuid(UUID networkId) {
        return networkId == null ? null : networkReconstructionSister.get(networkId);
    }

    /**
     * True when another reconstruction sister (not {@code except}) already holds this network.
     */
    public boolean hasOtherReconstruction(UUID networkId, @Nullable UUID except) {
        UUID holder = reconstructionSisterUuid(networkId);
        if (holder == null) {
            return false;
        }
        return except == null || !holder.equals(except);
    }

    public int networkSisterCount(UUID networkId) {
        if (networkId == null) {
            return 0;
        }
        var list = networkSisterOrder.get(networkId);
        return list == null ? 0 : list.size();
    }

    public List<UUID> pageSisters(UUID networkId, int offset, int limit) {
        if (networkId == null || limit <= 0) {
            return List.of();
        }
        var list = networkSisterOrder.get(networkId);
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
        if (!record.awakened || record.networkNodePos == null || record.starving || record.incapacitated || dirty) {
            return;
        }
        var level = server.overworld();
        var networkId = resolveNetworkIdCached(level, record.networkNodePos);
        float msk = MisakaComputeContribution.mskPerSecond(record.perception);
        float delta = nowIn ? msk : -msk;
        applyMskDelta(networkId, groupKey(record), delta);
    }

    private void applyMskDelta(UUID networkId, String groupKey, float delta) {
        if (delta == 0.0f || networkId == null) {
            return;
        }
        networkTotalMskPerSecond.merge(networkId, delta, Float::sum);
        float total = networkTotalMskPerSecond.getOrDefault(networkId, 0.0f);
        if (total <= 0.0f) {
            networkTotalMskPerSecond.remove(networkId);
        }
        var key = new NetworkGroupKey(networkId, groupKey);
        groupMskPerSecond.merge(key, delta, Float::sum);
        float group = groupMskPerSecond.getOrDefault(key, 0.0f);
        if (group <= 0.0f) {
            groupMskPerSecond.remove(key);
        }
    }

    /** Test hook: apply MSk delta without a live server. */
    public void testingApplyMskDelta(UUID networkId, String groupKey, float delta) {
        applyMskDelta(networkId, groupKey == null ? UNASSIGNED : groupKey, delta);
    }

    /** Test hook: clear aggregate maps without touching roster topology. */
    public void testingClearAggregates() {
        networkTotalMskPerSecond.clear();
        groupMskPerSecond.clear();
        coverageContributing.clear();
    }

    public boolean testingIsDirty() {
        return dirty;
    }

    public void testingSetDirty(boolean value) {
        dirty = value;
    }

    public static List<String> parseGroupKey(@Nullable String key) {
        if (key == null || key.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(key.split(GROUP_KEY_SEP, -1));
    }

    public static String encodeGroupKey(List<String> names) {
        if (names == null || names.isEmpty()) {
            return UNASSIGNED;
        }
        return String.join(GROUP_KEY_SEP, names);
    }

    private static String groupKey(MisakaSisterRecord record) {
        var names = FavorService.benevolentNames(record);
        return encodeGroupKey(names);
    }

    private static String privilegeName(MisakaSisterRecord record) {
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
        networkReconstructionSister.clear();
        groupMskPerSecond.clear();
        networkSisterOrder.clear();
        coverageContributing.clear();

        var level = server.overworld();
        if (topologyDirty) {
            MisakaNetworkRegistry.get(server).reconcileTopology(level);
        }

        var roster = MisakaSisterRoster.get(server);
        record SisterSort(
                UUID uuid,
                int serial,
                UUID networkId,
                float msk,
                String groupKey,
                String privilege,
                boolean reconstruction,
                boolean starving,
                boolean incapacitated,
                boolean inCoverage
        ) {
        }
        var sorted = new ArrayList<SisterSort>();

        for (var record : roster.all()) {
            if (!record.awakened || record.networkNodePos == null) {
                continue;
            }
            var networkId = resolveDuringRebuild(level, record.networkNodePos);
            float msk = MisakaComputeContribution.mskPerSecond(record.perception);
            String group = groupKey(record);
            String privilege = privilegeName(record);
            boolean reconstruction = record.isReconstruction || record.perception >= 101;
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
                    group,
                    privilege,
                    reconstruction,
                    record.starving,
                    record.incapacitated,
                    inCoverage
            ));
        }

        sorted.sort(Comparator.comparingInt(SisterSort::serial));
        var reconCandidates = new HashMap<UUID, SisterSort>();

        for (var sister : sorted) {
            networkSisterOrder
                    .computeIfAbsent(sister.networkId, ignored -> new ArrayList<>())
                    .add(sister.uuid);
            if (sister.reconstruction) {
                reconCandidates.putIfAbsent(sister.networkId, sister);
            }
            if (sister.starving || sister.incapacitated || !sister.inCoverage) {
                continue;
            }
            networkTotalMskPerSecond.merge(sister.networkId, sister.msk, Float::sum);
            groupMskPerSecond.merge(
                    new NetworkGroupKey(sister.networkId, sister.groupKey),
                    sister.msk,
                    Float::sum
            );
        }

        for (var entry : reconCandidates.entrySet()) {
            networkReconstructionPrivilege.put(entry.getKey(), entry.getValue().privilege());
            networkReconstructionSister.put(entry.getKey(), entry.getValue().uuid());
        }
    }

    private UUID resolveDuringRebuild(ServerLevel level, BlockPos nodePos) {
        var immutable = nodePos.immutable();
        var cached = nodeToNetworkId.get(immutable);
        if (cached != null) {
            return cached;
        }
        var resolved = MisakaNetworkRegistry.get(level.getServer()).resolveOrCreate(level, immutable);
        nodeToNetworkId.put(immutable, resolved);
        return resolved;
    }

    public record NetworkGroupKey(UUID networkId, String groupKey) {
        public NetworkGroupKey {
            networkId = networkId == null ? new UUID(0L, 0L) : networkId;
            groupKey = groupKey == null ? UNASSIGNED : groupKey;
        }

        public List<String> groupNames() {
            return parseGroupKey(groupKey);
        }
    }
}
