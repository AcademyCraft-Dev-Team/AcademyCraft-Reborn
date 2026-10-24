package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Unused energy laser towers on a wireless topology: not already bound to a satellite.
 */
public final class MisakaNetworkLasers {
    private MisakaNetworkLasers() {
    }

    public static List<BlockPos> listUnbound(ServerLevel level, @Nullable BlockPos connectedNodePos) {
        var result = new ArrayList<BlockPos>();
        if (level == null || connectedNodePos == null) {
            return result;
        }
        var server = level.getServer();
        if (server == null) {
            return result;
        }
        var networkId = MisakaNAT.get().resolveNetworkId(level, connectedNodePos);
        var registry = MisakaRelayRegistry.get(server);
        var data = WirelessNetworkData.get(level);
        for (var entry : data.getAllNodes().entrySet()) {
            var nodePos = entry.getKey();
            if (!MisakaNAT.get().resolveNetworkId(level, nodePos).equals(networkId)) {
                continue;
            }
            for (var userPos : entry.getValue().connectedUsers.keySet()) {
                if (!(level.getBlockEntity(userPos) instanceof EnergyLaserTowerBlockEntity tower)) {
                    continue;
                }
                var main = tower.mainEntity();
                if (main == null || !main.isMain()) {
                    continue;
                }
                var mainPos = main.getBlockPos().immutable();
                if (registry.laserBoundSatellite(level.dimension(), mainPos) != null) {
                    continue;
                }
                if (!result.contains(mainPos)) {
                    result.add(mainPos);
                }
            }
        }
        result.sort(BlockPos::compareTo);
        return result;
    }

    public static boolean canPower(ServerLevel level, BlockPos laserPos) {
        return level.getBlockEntity(laserPos) instanceof EnergyLaserTowerBlockEntity tower
                && tower.canPowerSatellite();
    }

    public record LaserRow(BlockPos pos, boolean ready) {
    }

    public static List<LaserRow> listUnboundRows(
            ServerLevel level,
            @Nullable BlockPos connectedNodePos,
            int limit
    ) {
        var selectable = listUnbound(level, connectedNodePos);
        int cap = Math.max(0, Math.min(limit, selectable.size()));
        var rows = new ArrayList<LaserRow>(cap);
        for (int i = 0; i < cap; i++) {
            var pos = selectable.get(i).immutable();
            rows.add(new LaserRow(pos, canPower(level, pos)));
        }
        return List.copyOf(rows);
    }
}
