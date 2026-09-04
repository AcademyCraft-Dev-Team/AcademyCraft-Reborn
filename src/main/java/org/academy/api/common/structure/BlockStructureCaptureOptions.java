package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Limits and policy used while turning world blocks into a movable structure.
 * The policy is evaluated on the server before any block is removed.
 */
public record BlockStructureCaptureOptions(
        int maximumBlocks,
        int maximumBlockEntityBytes,
        boolean gravityEnabled,
        boolean restoreWhenSettled,
        CapturePolicy policy,
        BlockStructureSettlementPolicy settlementPolicy
) {
    public static final int DEFAULT_MAXIMUM_BLOCKS = 256;
    public static final int DEFAULT_MAXIMUM_BLOCK_ENTITY_BYTES = 1_048_576;

    public BlockStructureCaptureOptions {
        if (maximumBlocks < 1 || maximumBlocks > BlockStructureSnapshot.MAX_BLOCKS) {
            throw new IllegalArgumentException("maximumBlocks must be between 1 and "
                    + BlockStructureSnapshot.MAX_BLOCKS);
        }
        if (maximumBlockEntityBytes < 0) {
            throw new IllegalArgumentException("maximumBlockEntityBytes cannot be negative");
        }
        if (policy == null || settlementPolicy == null) {
            throw new IllegalArgumentException("capture and settlement policies cannot be null");
        }
    }

    /** Compatibility constructor using natural-only falling settlement. */
    public BlockStructureCaptureOptions(
            int maximumBlocks,
            int maximumBlockEntityBytes,
            boolean gravityEnabled,
            boolean restoreWhenSettled,
            CapturePolicy policy
    ) {
        this(
                maximumBlocks,
                maximumBlockEntityBytes,
                gravityEnabled,
                restoreWhenSettled,
                policy,
                BlockStructureSettlementPolicy.NATURAL_BLOCKS
        );
    }

    /** Compatibility value retained for saved program data; settlement is unconditional. */
    @Override
    public boolean restoreWhenSettled() {
        return restoreWhenSettled;
    }

    public static BlockStructureCaptureOptions defaults() {
        return new BlockStructureCaptureOptions(
                DEFAULT_MAXIMUM_BLOCKS,
                DEFAULT_MAXIMUM_BLOCK_ENTITY_BYTES,
                true,
                false,
                CapturePolicy.MOVABLE,
                BlockStructureSettlementPolicy.NATURAL_BLOCKS
        );
    }

    public BlockStructureCaptureOptions withPolicy(CapturePolicy newPolicy) {
        return new BlockStructureCaptureOptions(
                maximumBlocks,
                maximumBlockEntityBytes,
                gravityEnabled,
                restoreWhenSettled,
                newPolicy,
                settlementPolicy
        );
    }

    public BlockStructureCaptureOptions withGravity(boolean enabled) {
        return new BlockStructureCaptureOptions(
                maximumBlocks,
                maximumBlockEntityBytes,
                enabled,
                restoreWhenSettled,
                policy,
                settlementPolicy
        );
    }

    public BlockStructureCaptureOptions withRestoreWhenSettled(boolean enabled) {
        return new BlockStructureCaptureOptions(
                maximumBlocks,
                maximumBlockEntityBytes,
                gravityEnabled,
                enabled,
                policy,
                settlementPolicy
        );
    }

    public BlockStructureCaptureOptions withSettlementPolicy(
            BlockStructureSettlementPolicy newPolicy
    ) {
        return new BlockStructureCaptureOptions(
                maximumBlocks,
                maximumBlockEntityBytes,
                gravityEnabled,
                restoreWhenSettled,
                policy,
                newPolicy
        );
    }

    @FunctionalInterface
    public interface CapturePolicy {
        CapturePolicy MOVABLE = (level, position, state, blockEntity) ->
                !state.isAir() && state.getDestroySpeed(level, position) >= 0.0f;

        boolean canCapture(
                ServerLevel level,
                BlockPos position,
                BlockState state,
                @Nullable BlockEntity blockEntity
        );
    }
}
