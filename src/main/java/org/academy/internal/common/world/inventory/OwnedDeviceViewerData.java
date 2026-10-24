package org.academy.internal.common.world.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.academy.internal.common.world.level.block.entity.OwnedDevice;
import org.jspecify.annotations.Nullable;

/**
 * One-slot ContainerData that tells the client whether the current viewer owns the device.
 */
public final class OwnedDeviceViewerData {
    public static final int INDEX = 0;

    private OwnedDeviceViewerData() {
    }

    public static ContainerData create() {
        return new SimpleContainerData(1);
    }

    public static void sync(ContainerData data, @Nullable OwnedDevice device, @Nullable Player player) {
        // Client factory menus keep network-synced values when the BE is not bound locally.
        if (device == null) {
            return;
        }
        data.set(INDEX, device.isOwner(player) ? 1 : 0);
    }

    public static boolean isOwner(ContainerData data) {
        return data.get(INDEX) != 0;
    }
}
