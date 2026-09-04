package org.academy.internal.common.structure;

import net.minecraft.core.BlockPos;
import org.academy.api.common.structure.BlockStructureSettlementMode;
import org.academy.api.common.structure.BlockStructureSettlementPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStructureSettlementMotionTest {
    @Test
    void gravitySettlementUsesFallingBlockGravity() {
        assertEquals(0.04, BlockStructureSettlementMotion.FALLING_BLOCK_GRAVITY);
    }

    @Test
    void stoppedOrBlockedPropulsionTransitionsIntoGravitySettlement() {
        assertTrue(BlockStructureSettlementMotion.shouldBegin(
                false, true, true, false, false, false));
        assertTrue(BlockStructureSettlementMotion.shouldBegin(
                false, true, false, true, false, false));
        assertFalse(BlockStructureSettlementMotion.shouldBegin(
                true, true, true, true, true, true));
        assertFalse(BlockStructureSettlementMotion.shouldBegin(
                false, false, false, true, false, true));
    }

    @Test
    void fallingCellsAreOrderedFromBottomLayerUpward() {
        var positions = new ArrayList<>(List.of(
                new BlockPos(0, 4, 0),
                new BlockPos(1, 2, 0),
                new BlockPos(0, 2, 0),
                new BlockPos(0, 3, 0)
        ));

        positions.sort(BlockStructureSettlementMotion::compareBottomUp);

        assertEquals(List.of(
                new BlockPos(0, 2, 0),
                new BlockPos(1, 2, 0),
                new BlockPos(0, 3, 0),
                new BlockPos(0, 4, 0)
        ), positions);
    }

    @Test
    void fixedUpperRegionDoesNotDelegateToFallingPolicy() {
        var policy = BlockStructureSettlementPolicy.fixedAbove(
                20,
                (_, _, _, _) -> BlockStructureSettlementMode.FALLING
        );

        assertEquals(BlockStructureSettlementMode.FALLING,
                policy.settlementMode(
                        null,
                        new BlockPos(0, 20, 0),
                        null,
                        null
                ));
        assertEquals(BlockStructureSettlementMode.FIXED,
                policy.settlementMode(
                        null,
                        new BlockPos(0, 21, 0),
                        null,
                        null
                ));
    }
}
