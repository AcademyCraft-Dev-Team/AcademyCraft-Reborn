package org.academy.internal.server.misaka;

import net.minecraft.network.chat.Component;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaComputeDevices;
import org.academy.api.common.misaka.MisakaComputePresence;
import org.academy.api.common.misaka.MisakaComputeSpendMode;
import org.academy.internal.common.world.item.LaserDesignatorItem;

/** Registers built-in Misaka compute devices (no new items). */
public final class MisakaComputeDevicesBootstrap {
    private static boolean registered;

    private MisakaComputeDevicesBootstrap() {
    }

    public static synchronized void registerBuiltins() {
        if (registered) {
            return;
        }
        registered = true;
        MisakaComputeDevices.register(MisakaComputeDevices.builder(AcademyCraft.academy("laser_designator"))
                .displayName(Component.translatable("item.academy.laser_designator"))
                .presence(MisakaComputePresence.HELD)
                .spendMode(MisakaComputeSpendMode.ACTUAL)
                .matches(LaserDesignatorItem::isDesignator)
                .build());
    }
}
