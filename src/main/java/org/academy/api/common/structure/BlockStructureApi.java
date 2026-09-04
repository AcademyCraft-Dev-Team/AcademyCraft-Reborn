package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.BlockStructureManager;

import java.util.Optional;

/** Entry point for creating movable structures from server-world blocks. */
public final class BlockStructureApi {
    private BlockStructureApi() {
    }

    public static BlockStructureCaptureResult capture(
            ServerLevel level,
            Iterable<BlockPos> positions
    ) {
        return capture(level, positions, BlockStructureCaptureOptions.defaults());
    }

    public static BlockStructureCaptureResult capture(
            ServerLevel level,
            Iterable<BlockPos> positions,
            BlockStructureCaptureOptions options
    ) {
        return BlockStructureManager.capture(level, positions, options);
    }

    /** Captures the six-directionally connected component containing {@code seed}. */
    public static BlockStructureCaptureResult captureConnected(
            ServerLevel level,
            BlockPos seed,
            BlockStructureCaptureOptions options
    ) {
        return BlockStructureManager.captureConnected(level, seed, options);
    }

    /** Generates a structure entity from existing snapshot data without changing world blocks. */
    public static Optional<BlockStructure> spawn(
            ServerLevel level,
            BlockStructureSnapshot snapshot,
            Vec3 position
    ) {
        return spawn(level, snapshot, position, true, false);
    }

    /** Generates a structure entity from existing snapshot data without changing world blocks. */
    public static Optional<BlockStructure> spawn(
            ServerLevel level,
            BlockStructureSnapshot snapshot,
            Vec3 position,
            boolean gravityEnabled,
            boolean restoreWhenSettled
    ) {
        return BlockStructureManager.spawn(
                level, snapshot, position, gravityEnabled, restoreWhenSettled);
    }
}
