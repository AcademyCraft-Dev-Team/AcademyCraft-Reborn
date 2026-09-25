package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockStructureGridAlignmentTest {
    @Test
    void yawSnapsToTheNearestQuarterTurn() {
        assertEquals(0, BlockStructureGridAlignment.nearestQuarterTurns(44.9f));
        assertEquals(1, BlockStructureGridAlignment.nearestQuarterTurns(45.1f));
        assertEquals(3, BlockStructureGridAlignment.nearestQuarterTurns(-90.0f));
        assertEquals(0, BlockStructureGridAlignment.nearestQuarterTurns(359.0f));
        assertEquals(0, BlockStructureGridAlignment.nearestQuarterTurns(Float.NaN));
    }

    @Test
    void clockwiseRotationPreservesVerticalOffsets() {
        var offset = new BlockPos(2, 3, -4);
        assertEquals(new BlockPos(4, 3, 2),
                BlockStructureGridAlignment.rotateOffset(offset, 1));
        assertEquals(new BlockPos(-2, 3, 4),
                BlockStructureGridAlignment.rotateOffset(offset, 2));
        assertEquals(new BlockPos(-4, 3, -2),
                BlockStructureGridAlignment.rotateOffset(offset, 3));
    }

    @Test
    void rectangularStructureGetsOneCoherentGridTranslation() {
        var alignment = BlockStructureGridAlignment.nearest(
                2,
                1,
                new BlockPos(0, 0, 0),
                new Vec3(10.2, 64.0, 20.2),
                88.0f
        );

        assertEquals(1, alignment.quarterTurns());
        assertEquals(Rotation.CLOCKWISE_90, alignment.rotation());
        assertEquals(new BlockPos(12, 64, 20),
                alignment.target(new BlockPos(0, 0, 0)));
        assertEquals(new BlockPos(12, 64, 21),
                alignment.target(new BlockPos(1, 0, 0)));
        assertEquals(new Vec3(10.5, 64.0, 20.5), alignment.entityPosition());
    }
}
