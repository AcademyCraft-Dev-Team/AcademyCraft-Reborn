package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.world.level.storage.MisakaNetworkRegistry;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WirelessForwardingMisakaNAT implements MisakaNAT {
    public static final WirelessForwardingMisakaNAT INSTANCE = new WirelessForwardingMisakaNAT();
    public static final int MANAGE_PAGE_SIZE = 64;

    private WirelessForwardingMisakaNAT() {
    }

    @Override
    public List<String> listAvailableNodes(ServerLevel level, BlockPos near) {
        return WirelessManager.getAvailableNodes(level, near);
    }

    @Override
    public Optional<BlockPos> findNode(ServerLevel level, String nodeName) {
        return Optional.ofNullable(WirelessNetworkData.get(level).findNodePositionByName(nodeName));
    }

    @Override
    public UUID resolveNetworkId(ServerLevel level, BlockPos nodePos) {
        return MisakaComputeIndex.get(level.getServer()).resolveNetworkIdCached(level, nodePos);
    }

    /**
     * Uncached resolve via {@link MisakaNetworkRegistry} (BFS component + stable UUID inheritance).
     * Prefer {@link #resolveNetworkId} / the compute-index cache on hot paths.
     */
    public static UUID resolveNetworkIdRaw(ServerLevel level, BlockPos nodePos) {
        if (level == null || nodePos == null) {
            return new UUID(0L, 0L);
        }
        return MisakaNetworkRegistry.get(level.getServer()).resolveOrCreate(level, nodePos);
    }

    @Override
    public boolean bindSisterToNode(ServerLevel level, UUID misakaUuid, BlockPos nodePos) {
        var data = WirelessNetworkData.get(level);
        if (data.getNodeConfig(nodePos) == null) {
            return false;
        }
        var server = level.getServer();
        var roster = MisakaSisterRoster.get(server);
        var record = roster.get(misakaUuid).orElse(null);
        if (record == null) {
            return false;
        }
        if ((record.isReconstruction || record.perception >= 101)
                && hasReconstructionWork(server, nodePos, misakaUuid)) {
            return false;
        }
        roster.modify(misakaUuid, sister -> sister.networkNodePos = nodePos.immutable());
        MisakaComputeIndex.get(server).markDirty();
        roster.get(misakaUuid).ifPresent(updated -> {
            PerceptionService.tryIntegrateIfEligible(server, updated);
            MisakaComputeContribution.refreshCpForRecord(server, updated);
        });
        return true;
    }

    @Override
    public boolean unbindSister(MinecraftServer server, UUID misakaUuid) {
        var roster = MisakaSisterRoster.get(server);
        if (roster.get(misakaUuid).isEmpty()) {
            return false;
        }
        roster.modify(misakaUuid, sister -> {
            sister.networkNodePos = null;
            sister.wanderAnchorChunk = null;
        });
        MisakaComputeIndex.get(server).markDirty();
        roster.get(misakaUuid).ifPresent(record -> MisakaComputeContribution.refreshCpForRecord(server, record));
        return true;
    }

    @Override
    public boolean hasReconstructionWork(MinecraftServer server, BlockPos nodePos, @Nullable UUID except) {
        var level = server.overworld();
        var targetNetwork = resolveNetworkId(level, nodePos);
        var index = MisakaComputeIndex.get(server);
        index.rebuildIfDirty(server);
        return index.hasOtherReconstruction(targetNetwork, except);
    }

    @Override
    public int countNetworkSisters(ServerLevel level, BlockPos nodePos) {
        var index = MisakaComputeIndex.get(level.getServer());
        index.rebuildIfDirty(level.getServer());
        var networkId = resolveNetworkId(level, nodePos);
        return index.networkSisterCount(networkId);
    }

    @Override
    public List<UUID> listNetworkSisters(
            ServerLevel level,
            BlockPos nodePos,
            int offset,
            int limit
    ) {
        var index = MisakaComputeIndex.get(level.getServer());
        index.rebuildIfDirty(level.getServer());
        var networkId = resolveNetworkId(level, nodePos);
        return index.pageSisters(networkId, offset, limit);
    }

    @Override
    public boolean canUseMisakaService(ServerLevel level, UUID networkId, BlockPos pos) {
        return MisakaNetworkCoverage.canUseMisakaService(level, networkId, pos);
    }
}
