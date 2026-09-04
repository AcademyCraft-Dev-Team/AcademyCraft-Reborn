package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.BlockStructureManager;

import java.util.List;
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

    /** Finds a connected movable component without modifying the world. */
    public static BlockStructureSelectionResult selectConnected(
            ServerLevel level,
            BlockPos seed,
            int maximumBlocks,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        return BlockStructureManager.selectConnected(
                level, seed, maximumBlocks, policy);
    }

    /** Finds every capturable block whose grid position is inside a spherical volume. */
    public static BlockStructureSelectionResult selectSphere(
            ServerLevel level,
            BlockPos center,
            double radius,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        return BlockStructureManager.selectSphere(level, center, radius, policy);
    }

    /**
     * Recursively removes candidates that would immediately meet an external block while moving
     * one grid step in {@code movementDirection}. The world is not modified.
     */
    public static List<BlockPos> cropImmediatelyBlocked(
            ServerLevel level,
            Iterable<BlockPos> candidates,
            Direction movementDirection
    ) {
        return BlockStructureManager.cropImmediatelyBlocked(
                level, candidates, movementDirection);
    }

    /**
     * Generates a structure entity from existing snapshot data without changing
     * world blocks. The legacy restore flag is retained for data compatibility;
     * every stopped structure now settles to blocks or drops.
     */
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
