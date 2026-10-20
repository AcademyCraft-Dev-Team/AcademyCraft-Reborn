package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.NativeStructureCollisionBoxes;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLevelTerrainContactGridTest {
    @Test
    void liftingPlatformDoesNotExhaustBudgetBeforeReachingItsCanopy() {
        var bounds = SubLevelTerrainBreakPolicy.sweptContactBounds(
                List.of(new AABB(0, 0, 0, 9, 1, 6)),
                StructurePose.identity(new Vec3(-151, 94, 62)),
                StructurePose.identity(new Vec3(-151, 94.3, 62)));
        assertEquals(-151, bounds.minX);
        assertEquals(95.3, bounds.maxY, 1.0E-9);
        long cells = (long) (Math.ceil(bounds.maxX) - Math.floor(bounds.minX))
                * (long) (Math.ceil(bounds.maxY) - Math.floor(bounds.minY))
                * (long) (Math.ceil(bounds.maxZ) - Math.floor(bounds.minZ));
        assertEquals(108L, cells);
    }

    @Test
    void rotatingTranslatedCornerRemainsInsideContactBounds() {
        var box = new AABB(7, 0, -1, 9, 2, 1);
        var start = new StructurePose(new Vec3(-151, 94, 62),
                new Quaterniond().rotateX(0.2).rotateY(-0.4));
        var end = new StructurePose(new Vec3(-149, 95, 65),
                new Quaterniond().rotateX(1.1).rotateY(2.0));
        var bounds = SubLevelTerrainBreakPolicy.sweptContactBounds(List.of(box), start, end);
        for (int sample = 0; sample <= 100; sample++) {
            var actual = NativeStructureCollisionBoxes.transformBox(box, start.lerp(end, sample / 100.0));
            assertTrue(bounds.minX <= actual.minX && bounds.maxX >= actual.maxX);
            assertTrue(bounds.minY <= actual.minY && bounds.maxY >= actual.maxY);
            assertTrue(bounds.minZ <= actual.minZ && bounds.maxZ >= actual.maxZ);
        }
    }

    @Test
    void mergedBodyStillFindsLeavesAtItsSurface() {
        var grid = new SubLevelTerrainBreakPolicy.LocalGrid(List.of(new AABB(0, 0, 0, 8, 2, 8)));
        assertTrue(grid.intersects(StructurePose.identity(new Vec3(0, 0.2, 0)),
                new AABB(3, 2, 3, 4, 3, 4)));
        assertFalse(grid.intersects(StructurePose.identity(Vec3.ZERO),
                new AABB(3, 3, 3, 4, 4, 4)));
    }

    @Test
    void rotatedBroadPhaseCornerDoesNotDestroyUncontactedPlants() {
        var grid = new SubLevelTerrainBreakPolicy.LocalGrid(List.of(new AABB(0, 0, 0, 1, 1, 1)));
        var pose = new StructurePose(Vec3.ZERO, new Quaterniond().rotateY(Math.PI / 4));
        assertFalse(grid.intersects(pose, new AABB(0, 0.1, 0.5, 0.3, 0.9, 0.7)));
        assertTrue(grid.intersects(pose, new AABB(0.6, 0.1, -0.1, 0.8, 0.9, 0.1)));
    }

    @Test
    void hollowInteriorDoesNotBreakPlants() {
        var grid = new SubLevelTerrainBreakPolicy.LocalGrid(List.of(
                new AABB(0, 0, 0, 1, 2, 4), new AABB(3, 0, 0, 4, 2, 4)));
        assertFalse(grid.intersects(StructurePose.identity(Vec3.ZERO), new AABB(1, 0, 1, 2, 1, 2)));
    }
}
