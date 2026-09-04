package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/** Result of restoring a movable structure to the world grid. */
public record BlockStructureRestoreResult(
        Status status,
        Optional<BlockPos> problemPosition,
        int affectedBlocks
) {
    public BlockStructureRestoreResult {
        problemPosition = problemPosition == null ? Optional.empty() : problemPosition;
        affectedBlocks = Math.max(0, affectedBlocks);
    }

    public static BlockStructureRestoreResult success(int affectedBlocks) {
        return new BlockStructureRestoreResult(Status.SUCCESS, Optional.empty(), affectedBlocks);
    }

    public static BlockStructureRestoreResult failure(Status status, BlockPos problemPosition) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("SUCCESS is not a failure");
        return new BlockStructureRestoreResult(status, Optional.ofNullable(problemPosition), 0);
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        INVALID_STRUCTURE,
        WRONG_LEVEL,
        UNLOADED,
        OUT_OF_WORLD,
        OCCUPIED,
        WORLD_CHANGE_FAILED
    }
}
