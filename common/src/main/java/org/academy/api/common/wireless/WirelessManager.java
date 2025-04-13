package org.academy.api.common.wireless;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WirelessManager {
    public static final Map<BlockPos, WirelessNode> WIRELESS_NODES = new HashMap<>();

    // 选择第一个
    @Nullable
    public static WirelessNode getWirelessNode(String name) {
        for (WirelessNode node : WIRELESS_NODES.values()) {
            if (node.getNodeName().equals(name)) {
                return node;
            }
        }
        return null;
    }

    public static List<WirelessNode> getAvailableWirelessMasters(@NotNull BlockPos nodePos) {
        List<WirelessNode> result = new ArrayList<>();

        for (Map.Entry<BlockPos, WirelessNode> entry : WIRELESS_NODES.entrySet()) {
            BlockPos masterPos = entry.getKey();
            WirelessNode master = entry.getValue();

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
