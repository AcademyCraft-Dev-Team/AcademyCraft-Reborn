package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelCaptureSourceTest {
    @Test
    void voxelBoxesUseCellOffsetFromNonzeroSelectionOrigin() {
        var origin = new BlockPos(-79, 120, 47);
        var cube = new AABB(0, 0, 0, 1, 1, 1);
        assertEquals(cube, LevelCaptureSource.localBox(cube, origin, origin));
        assertEquals(new AABB(2, 1, 3, 3, 2, 4),
                LevelCaptureSource.localBox(cube, origin.offset(2, 1, 3), origin));
    }

    @Test
    void partialVoxelShapeKeepsItsWithinCellExtent() {
        var origin = new BlockPos(327, 81, -276);
        var slab = new AABB(0, 0.5, 0, 1, 1, 1);
        assertEquals(new AABB(1, 2.5, 4, 2, 3, 5),
                LevelCaptureSource.localBox(slab, origin.offset(1, 2, 4), origin));
    }
}
