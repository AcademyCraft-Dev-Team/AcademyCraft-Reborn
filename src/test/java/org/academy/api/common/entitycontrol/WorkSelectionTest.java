package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkSelectionTest {
    @Test void previewUsesTheExactInclusiveBlockRegionAtNegativeCoordinates() {
        var bounds = WorkSelection.bounds(new BlockPos(-1, 64, -2), new BlockPos(-4, 100, -7), 3, -2);
        var region = WorkSelection.region(Identifier.withDefaultNamespace("overworld"), bounds);
        assertEquals(new BlockPos(-4, 60, -7), region.minimum());
        assertEquals(new BlockPos(-1, 62, -2), region.maximum());
        assertEquals(region.volume(), (int) (bounds.getXsize() * bounds.getYsize() * bounds.getZsize()));
    }
    @Test void selectionUsesEntityBoundsRatherThanProjectedCenter() {
        var area = WorkSelection.bounds(BlockPos.ZERO, BlockPos.ZERO, 1, 0);
        assertTrue(area.intersects(new AABB(.9, .1, .1, 1.9, .9, .9)));
        assertFalse(area.intersects(new AABB(1, .1, .1, 2, .9, .9)));
    }
    @Test void locksHeightAndRejectsParallelOrBackwardRays() {
        assertEquals(new Vec3(2, 4, 2), WorkSelection.intersectHorizontal(new Vec3(2, 20, 2), new Vec3(0, -1, 0), 4));
        assertNull(WorkSelection.intersectHorizontal(Vec3.ZERO, new Vec3(1, 0, 0), 4));
        assertNull(WorkSelection.intersectHorizontal(Vec3.ZERO, new Vec3(0, -1, 0), 4));
    }
}
