package org.academy.internal.client.misaka;

import net.minecraft.resources.Identifier;
import org.academy.internal.common.network.misaka.MisakaDeviceQuotaSyncPacket;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client cache of Misaka device quota rows for tooltip + App. */
public final class MisakaDeviceQuotaClient {
    private static float playerBudgetMskPerSec;
    private static int remainingPercentSlots = 100;
    private static Map<Identifier, MisakaDeviceQuotaSyncPacket.DeviceQuotaRow> rows = Map.of();

    private MisakaDeviceQuotaClient() {
    }

    public static void apply(MisakaDeviceQuotaSyncPacket packet) {
        if (packet == null) {
            return;
        }
        playerBudgetMskPerSec = packet.playerBudgetMskPerSec();
        remainingPercentSlots = packet.remainingPercentSlots();
        var next = new HashMap<Identifier, MisakaDeviceQuotaSyncPacket.DeviceQuotaRow>();
        for (var row : packet.rows()) {
            next.put(row.id(), row);
        }
        rows = Map.copyOf(next);
    }

    public static float playerBudgetMskPerSec() {
        return playerBudgetMskPerSec;
    }

    public static int remainingPercentSlots() {
        return remainingPercentSlots;
    }

    public static MisakaDeviceQuotaSyncPacket.@Nullable DeviceQuotaRow row(Identifier id) {
        return id == null ? null : rows.get(id);
    }

    public static List<MisakaDeviceQuotaSyncPacket.DeviceQuotaRow> allRows() {
        return List.copyOf(rows.values());
    }
}
