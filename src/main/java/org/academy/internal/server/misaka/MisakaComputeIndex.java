package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
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

    private void rebuild(MinecraftServer server) {
        nodeToNetworkId.clear();
        networkTotalMskPerSecond.clear();
        networkReconstructionPrivilege.clear();
        groupMskPerSecond.clear();
        networkSisterOrder.clear();

        var level = server.overworld();
        var roster = MisakaSisterRoster.get(server);
        record SisterSort(
                UUID uuid,
                int serial,
                BlockPos networkId,
                float msk,
                String closest,
                boolean reconstruction,
                boolean starving
        ) {
        }
        var sorted = new ArrayList<SisterSort>();

        for (var record : roster.all()) {
            // Bound awakened sisters stay on the manage list / recon pick even while starving.
            // Only non-starving sisters contribute MSk.
            if (!record.awakened || record.networkNodePos == null) {
                continue;
            }
            var networkId = resolveDuringRebuild(level, record.networkNodePos);
            float msk = MisakaComputeContribution.mskPerSecond(record.perception);
            String closest = record.lastInteractedBenevolentPlayerName == null
                    ? UNASSIGNED
                    : record.lastInteractedBenevolentPlayerName;
            if (closest.isEmpty()
                    || !FavorService.isPrivilegePlayer(
                    record, closest)) {
                closest = UNASSIGNED;
            }
            boolean reconstruction = record.perception >= 101;
            sorted.add(new SisterSort(
                    record.misakaUuid,
                    record.serial,
                    networkId,
                    msk,
                    closest,
                    reconstruction,
                    record.starving
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
            if (sister.starving) {
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
