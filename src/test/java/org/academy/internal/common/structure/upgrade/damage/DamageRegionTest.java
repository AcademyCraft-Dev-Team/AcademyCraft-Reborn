package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DamageRegionTest {
    @Test void emptySpaceBetweenSeparatedBoundsIsNotEnumerated() {
        assertFalse(DamageRegion.cells(new Vec3(40, 0, 0), 1, new AABB(0, 0, 0, 2, 2, 2)).hasNext());
    }
    @Test void zeroRadiusRetainsTerrainContainingCellSemantics() {
        var point = new Vec3(-2.1, 64.9, 5.0);
        assertTrue(DamageRegion.hostCell(point, 0, new BlockPos(-3, 64, 5)));
        assertFalse(DamageRegion.hostCell(point, 0, new BlockPos(-2, 64, 5)));
    }
    @Test void thinShapeAndHollowCellDoNotUseFullCubeBounds() {
        var lowerSlab = new AABB(0, 0, 0, 1, .5, 1);
        assertFalse(DamageRegion.intersects(new Vec3(.5, .9, .5), .1, lowerSlab));
        assertTrue(DamageRegion.intersects(new Vec3(.5, .6, .5), .11, lowerSlab));
        assertFalse(DamageRegion.intersects(new Vec3(.5, .5, .5), .1, new AABB(0, 0, 0, .1, 1, 1)));
    }
    @Test void rotatedTranslatedSphereUsesLocalShapeWithoutEnlargingIt() {
        var pose = new StructurePose(new Vec3(-2160, 150, -1480), new Quaterniond().rotateXYZ(.4, .8, .2));
        var local = new Vec3(.5, .9, .5);
        var query = pose.worldToLocalPoint(pose.localToWorldPoint(local));
        assertFalse(DamageRegion.intersects(query, .1, new AABB(0, 0, 0, 1, .5, 1)));
        assertTrue(DamageRegion.intersects(query, .41, new AABB(0, 0, 0, 1, .5, 1)));
    }
    @Test void sphereCornerUsesEuclideanDistance() {
        var box = new AABB(0, 0, 0, 1, 1, 1);
        assertFalse(DamageRegion.intersects(new Vec3(1.8, 1.8, 1.8), 1, box));
        assertTrue(DamageRegion.intersects(new Vec3(1.5, 1.5, 1.5), 1, box));
    }
    @Test void maximumRadiusEnumerationIsLazy() {
        var cells = DamageRegion.cells(Vec3.ZERO, 256, new AABB(-300, -300, -300, 300, 300, 300));
        assertEquals(new BlockPos(-256, -256, -256), cells.next());
        assertTrue(cells.hasNext());
    }
}
