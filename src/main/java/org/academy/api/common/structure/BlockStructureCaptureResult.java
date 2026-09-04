package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/** Result of an atomic structure capture request. */
public record BlockStructureCaptureResult(
        Status status,
        Optional<BlockStructure> structure,
        Optional<BlockPos> problemPosition,
        int affectedBlocks
) {
    public BlockStructureCaptureResult {
        structure = structure == null ? Optional.empty() : structure;
        problemPosition = problemPosition == null ? Optional.empty() : problemPosition;
        affectedBlocks = Math.max(0, affectedBlocks);
    }

    public static BlockStructureCaptureResult success(BlockStructure structure, int affectedBlocks) {
        return new BlockStructureCaptureResult(
                Status.SUCCESS,
                Optional.of(structure),
                Optional.empty(),
                affectedBlocks
        );
    }

    public static BlockStructureCaptureResult failure(Status status, BlockPos problemPosition) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("SUCCESS requires a structure");
        return new BlockStructureCaptureResult(
                status,
                Optional.empty(),
                Optional.ofNullable(problemPosition),
                0
        );
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        EMPTY,
        TOO_LARGE,
        UNLOADED,
        OUT_OF_WORLD,
        REJECTED,
        BLOCK_ENTITY_DATA_TOO_LARGE,
        COLLISION_DATA_TOO_LARGE,
        WORLD_CHANGE_FAILED,
        ENTITY_SPAWN_FAILED
    }
}
