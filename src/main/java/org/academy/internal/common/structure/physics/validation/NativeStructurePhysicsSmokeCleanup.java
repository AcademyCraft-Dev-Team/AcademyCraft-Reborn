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
        var storage = runtime.storageLevel(reference).orElse(null);
        if (storage == null) return;
        try {
            // The captured set is not always the fixture's source cells; empty every stored cell.
            for (int chunkX = -2; chunkX < 2; chunkX++) {
                for (int chunkZ = -2; chunkZ < 2; chunkZ++) {
                    var chunk = storage.getChunk(chunkX, chunkZ);
                    var sections = chunk.getSections();
                    for (int index = 0; index < sections.length; index++) {
                        if (sections[index].hasOnlyAir()) continue;
                        int baseY = storage.getMinY() + index * 16;
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    if (sections[index].getBlockState(x, y, z).isAir()) continue;
                                    var local = new BlockPos(chunkX * 16 + x, baseY + y, chunkZ * 16 + z);
                                    var before = handle.world().read(local);
                                    handle.world().compareAndSet(local, before,
                                            new StructureBlockData(Blocks.AIR.defaultBlockState()));
                                }
                            }
                        }
                    }
                }
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
}
