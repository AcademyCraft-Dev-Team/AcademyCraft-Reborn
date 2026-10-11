package org.academy.api.common.structure;

/**
 * Result of materializing a stopped structure. Failed placement retains the complete
 * structure; droppedBlocks is retained for source compatibility and is zero on this path.
 */
public record BlockStructureSettlementResult(
        Status status,
        int placedBlocks,
        int droppedBlocks
) {
    public BlockStructureSettlementResult {
        placedBlocks = Math.max(0, placedBlocks);
        droppedBlocks = Math.max(0, droppedBlocks);
    }

    public static BlockStructureSettlementResult success(
            int placedBlocks,
            int droppedBlocks
    ) {
        return new BlockStructureSettlementResult(
                Status.SUCCESS, placedBlocks, droppedBlocks);
    }

    public static BlockStructureSettlementResult failure(Status status) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("SUCCESS is not a failure");
        }
        return new BlockStructureSettlementResult(status, 0, 0);
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public int affectedBlocks() {
        return placedBlocks + droppedBlocks;
    }

    public enum Status {
        SUCCESS,
        INVALID_STRUCTURE,
        WRONG_LEVEL,
        RETAINED,
        RECOVERY_REQUIRED
    }
}
