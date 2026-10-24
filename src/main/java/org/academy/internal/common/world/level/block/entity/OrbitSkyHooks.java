package org.academy.internal.common.world.level.block.entity;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Client registers hooks from {@code MisakaOrbitSkyClient}; dedicated server keeps no-ops.
 * Keeps {@link EnergyLaserTowerBlockEntity} free of client-only class references.
 */
public final class OrbitSkyHooks {
    private static Consumer<EnergyLaserTowerBlockEntity> syncHook = be -> {
    };
    private static Consumer<BlockPos> removeHook = pos -> {
    };

    private OrbitSkyHooks() {
    }

    public static void setHooks(
            Consumer<EnergyLaserTowerBlockEntity> sync,
            Consumer<BlockPos> remove
    ) {
        syncHook = sync != null ? sync : be -> {
        };
        removeHook = remove != null ? remove : pos -> {
        };
    }

    public static void sync(@Nullable EnergyLaserTowerBlockEntity tower) {
        if (tower != null) {
            syncHook.accept(tower);
        }
    }

    public static void remove(@Nullable BlockPos mainPos) {
        if (mainPos != null) {
            removeHook.accept(mainPos);
        }
    }
}
