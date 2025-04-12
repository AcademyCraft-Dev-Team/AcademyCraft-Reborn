package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WirelessManager {
    public static final Map<BlockPos, WirelessMaster> WIRELESS_MASTERS = new HashMap<>();

    public static List<WirelessMaster> getAvailableWirelessMasters(@NotNull BlockPos nodePos) {
        List<WirelessMaster> result = new ArrayList<>();

        for (Map.Entry<BlockPos, WirelessMaster> entry : WIRELESS_MASTERS.entrySet()) {
            BlockPos masterPos = entry.getKey();
            WirelessMaster master = entry.getValue();

            int radius = master.getRadius();

            if (masterPos.distSqr(nodePos) <= radius * radius) {
                result.add(master);
            }
        }

        return result;
    }

    private WirelessManager() {
    }
}
