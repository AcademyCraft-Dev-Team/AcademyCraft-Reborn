package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WirelessForwardingMisakaNAT implements MisakaNAT {
    public static final WirelessForwardingMisakaNAT INSTANCE = new WirelessForwardingMisakaNAT();

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
    public BlockPos resolveNetworkId(ServerLevel level, BlockPos nodePos) {
        var data = WirelessNetworkData.get(level);
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(nodePos.immutable());
        BlockPos min = nodePos.immutable();
        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (!seen.add(current)) {
                continue;
            }
            if (current.asLong() < min.asLong()) {
                min = current;
            }
            var config = data.getNodeConfig(current);
            if (config == null) {
                continue;
            }
            for (var userPos : config.connectedUsers.keySet()) {
                if (data.getNodeConfig(userPos) != null) {
                    queue.add(userPos.immutable());
                }
            }
        }
        return min;
    }

    @Override
    public boolean bindSisterToNode(ServerLevel level, UUID misakaUuid, BlockPos nodePos) {
        var data = WirelessNetworkData.get(level);
        if (data.getNodeConfig(nodePos) == null) {
            return false;
        }
        var roster = MisakaSisterRoster.get(level.getServer());
        var record = roster.get(misakaUuid).orElse(null);
        if (record == null) {
            return false;
        }
        if (record.perception >= 101
                && hasReconstructionWork(level.getServer(), nodePos, misakaUuid)) {
            return false;
        }
        roster.modify(misakaUuid, sister -> sister.networkNodePos = nodePos.immutable());
        MisakaComputeContribution.refreshCpForRecord(level.getServer(), record);
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
        roster.get(misakaUuid).ifPresent(record -> MisakaComputeContribution.refreshCpForRecord(server, record));
        return true;
    }

    @Override
    public boolean hasReconstructionWork(MinecraftServer server, BlockPos nodePos, @Nullable UUID except) {
        var level = server.overworld();
        var targetNetwork = resolveNetworkId(level, nodePos);
        return MisakaSisterRoster.get(server).all().stream()
                .filter(record -> record.awakened && record.perception >= 101 && record.networkNodePos != null)
                .filter(record -> except == null || !record.misakaUuid.equals(except))
                .anyMatch(record -> resolveNetworkId(level, record.networkNodePos).equals(targetNetwork));
    }
}
