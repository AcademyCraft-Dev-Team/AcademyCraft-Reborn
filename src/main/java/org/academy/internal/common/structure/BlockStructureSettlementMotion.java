package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;

/** Pure transition rules for the gravel-like terminal motion of block structures. */
public final class BlockStructureSettlementMotion {
    public static final double FALLING_BLOCK_GRAVITY = 0.04;

    private BlockStructureSettlementMotion() {
    }

    public static boolean shouldBegin(
            boolean settlementPrevented,
            boolean stopped,
            boolean fullyStopped,
            boolean horizontalCollision,
            boolean verticalCollision,
            boolean propulsionFinished
    ) {
        return !settlementPrevented && stopped && (fullyStopped
                || horizontalCollision || verticalCollision || propulsionFinished);
    }

    /** Orders falling cells by layer first so lower blocks enter the world before upper blocks. */
    public static int compareBottomUp(BlockPos left, BlockPos right) {
        var y = Integer.compare(left.getY(), right.getY());
        if (y != 0) return y;
        var x = Integer.compare(left.getX(), right.getX());
        return x != 0 ? x : Integer.compare(left.getZ(), right.getZ());
    }
}
