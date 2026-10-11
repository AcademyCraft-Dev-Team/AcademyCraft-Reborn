package org.academy.internal.server.misaka;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.misaka.MisakaComputeDevices;
import org.academy.internal.common.network.misaka.MisakaDeviceQuotaSyncPacket;
import org.academy.internal.server.world.level.storage.MisakaPlayerDeviceAllocations;
import org.misaka.MisakaNetworkServer;

import java.util.ArrayList;

/** Pushes device quota rows to clients for tooltip + App. */
public final class MisakaDeviceQuotaSync {
    private MisakaDeviceQuotaSync() {
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) {
            return;
        }
        var access = ServerMisakaComputeAccess.INSTANCE;
        var stored = MisakaPlayerDeviceAllocations.get(player.level().getServer());
        var effective = access.effectiveSnapshot(player);
        var lastAll = stored.lastPercents(player.getUUID());
        var rows = new ArrayList<MisakaDeviceQuotaSyncPacket.DeviceQuotaRow>();
        var seen = new java.util.HashSet<>(effective.keySet());
        seen.addAll(lastAll.keySet());
        for (var id : seen.stream().sorted().toList()) {
            var device = MisakaComputeDevices.get(id);
            boolean present = device != null && MisakaDevicePresence.isPresent(player, device);
            rows.add(new MisakaDeviceQuotaSyncPacket.DeviceQuotaRow(
                    id,
                    effective.getOrDefault(id, 0),
                    lastAll.getOrDefault(id, 0),
                    present
            ));
        }
        MisakaNetworkServer.send(player, new MisakaDeviceQuotaSyncPacket(
                access.playerBudgetMskPerSecond(player),
                access.remainingPercentSlots(player),
                rows
        ));
    }
}
