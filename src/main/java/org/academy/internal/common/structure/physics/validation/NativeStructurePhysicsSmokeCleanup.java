package org.academy.internal.common.structure.physics.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.NativeStructureRuntime;

import java.util.List;

/**
 * Opt-in fixture cleanup: empties a captured structure and returns its permanent native slot.
 * Retention was already asserted by the owning probe; this only keeps the chained matrix
 * inside the four-slot container budget.
 */
final class NativeStructurePhysicsSmokeCleanup {
    private NativeStructurePhysicsSmokeCleanup() {
    }

    static void releaseCapturedSlot(ServerLevel host, StructureRef reference, List<BlockPos> cells) {
        if (cells.isEmpty()) return;
        var runtime = NativeStructureRuntime.backend(host.getServer());
        var handle = runtime.resolve(reference).orElse(null);
        if (handle == null) return;
        var origin = minimumCorner(cells);
        try {
            for (var position : cells) {
                var local = position.subtract(origin);
                var before = handle.world().read(local);
                if (before.state().isAir()) continue;
                handle.world().compareAndSet(local, before,
                        new StructureBlockData(Blocks.AIR.defaultBlockState()));
            }
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.warn("Structure smoke cleanup could not empty {}", reference, failure);
            return;
        }
        if (!runtime.unload(reference)) return;
        if (!runtime.discardEmpty(reference)) {
            AcademyCraft.LOGGER.warn("Structure smoke cleanup could not release the slot of {}", reference);
        }
    }

    private static BlockPos minimumCorner(List<BlockPos> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (var position : cells) {
            minX = Math.min(minX, position.getX());
            minY = Math.min(minY, position.getY());
            minZ = Math.min(minZ, position.getZ());
        }
        return new BlockPos(minX, minY, minZ);
    }
}
