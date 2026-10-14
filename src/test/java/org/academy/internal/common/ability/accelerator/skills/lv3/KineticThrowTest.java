package org.academy.internal.common.ability.accelerator.skills.lv3;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticThrowTest {
    @Test
    void chargeTicksSelectThreeBoundedGeometryTiers() {
        assertEquals(0, KineticThrow.chargeTier(0));
        assertEquals(0, KineticThrow.chargeTier(19));
        assertEquals(1, KineticThrow.chargeTier(20));
        assertEquals(1, KineticThrow.chargeTier(39));
        assertEquals(2, KineticThrow.chargeTier(40));
        assertEquals(2, KineticThrow.chargeTier(Long.MAX_VALUE));

        assertEquals(5, KineticThrow.horizontalRadius(0));
        assertEquals(7, KineticThrow.horizontalRadius(1));
        assertEquals(9, KineticThrow.horizontalRadius(2));
        assertEquals(2, KineticThrow.verticalRadius(0));
        assertEquals(4, KineticThrow.verticalRadius(1));
        assertEquals(6, KineticThrow.verticalRadius(2));
        assertEquals(5, KineticThrow.cylinderRadius(0));
        assertEquals(7, KineticThrow.cylinderRadius(1));
        assertEquals(9, KineticThrow.cylinderRadius(2));
        assertEquals(5, KineticThrow.cylinderHeight(0));
        assertEquals(7, KineticThrow.cylinderHeight(1));
        assertEquals(9, KineticThrow.cylinderHeight(2));
        assertEquals(4, KineticThrow.liftHeight(0));
        assertEquals(5, KineticThrow.liftHeight(1));
        assertEquals(7, KineticThrow.liftHeight(2));
        assertEquals(5, KineticThrow.explosionRadius(0));
        assertEquals(7, KineticThrow.explosionRadius(1));
        assertEquals(9, KineticThrow.explosionRadius(2));
    }

    @Test
    void damageIsFivePerCapturedBlock() {
        assertEquals(0.0f, KineticThrow.structureDamage(0));
        assertEquals(5.0f, KineticThrow.structureDamage(1));
        assertEquals(640.0f, KineticThrow.structureDamage(128));
    }

    @Test
    void throwDirectionRemainsParallelToPlayerView() {
        var view = new Vec3(4.0, -2.0, 7.0);
        var direction = KineticThrow.throwDirection(view);

        assertEquals(1.0, direction.length(), 1.0e-9);
        assertEquals(1.0, direction.dot(view.normalize()), 1.0e-9);
    }

    @Test
    void cropToBudgetKeepsSeedAndNearestCellsDeterministically() {
        var seed = new BlockPos(0, 0, 0);
        var positions = List.of(seed, new BlockPos(5, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0),
                new BlockPos(-3, 0, 0), new BlockPos(0, 4, 0), new BlockPos(1, 0, 0));
        var cropped = KineticThrow.cropToBudget(positions, seed, 3);

        assertEquals(List.of(seed, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)), cropped);
        assertEquals(cropped, KineticThrow.cropToBudget(new ArrayList<>(
                positions.reversed()), seed, 3));
        assertEquals(List.of(seed), KineticThrow.cropToBudget(
                List.of(new BlockPos(9, 9, 9), seed, seed), seed, 1));
        assertTrue(KineticThrow.cropToBudget(positions, seed, 0).isEmpty());
    }

    @Test
    void regionBoundsCoverStructureAndLiftWithinHostLimits() {
        var positions = List.of(new BlockPos(10, 100, 20), new BlockPos(12, 100, 22));
        var limits = new AABB(-100, -64, -100, 100, 320, 100);
        var bounds = KineticThrow.regionBounds(positions, 4, -64, 320, limits);

        assertNotNull(bounds);
        assertEquals(15.0, bounds.getXsize(), 1.0e-9);
        assertEquals(17.0, bounds.getYsize(), 1.0e-9);
        assertEquals(15.0, bounds.getZsize(), 1.0e-9);
        assertTrue(bounds.minY <= 100 && bounds.maxY >= 105);
        assertNull(KineticThrow.regionBounds(positions, 4, 100, 104, limits));
    }

    @Test
    void followBoundsRecentersAheadAndRejectsUncontainableOccupancy() {
        var region = new AABB(0, 0, 0, 10, 10, 10);
        var occupied = new AABB(8, 2, 2, 11, 5, 5);
        var limits = new AABB(-100, -64, -100, 100, 320, 100);
        var followed = KineticThrow.followBounds(region, occupied, new Vec3(50, 0, 0), 0.5, 1.0,
                -64, 320, limits);

        assertNotNull(followed);
        assertEquals(region.getXsize(), followed.getXsize(), 1.0e-9);
        assertEquals(region.getYsize(), followed.getYsize(), 1.0e-9);
        assertTrue(followed.minX <= occupied.minX && followed.maxX >= occupied.maxX
                && followed.minY <= occupied.minY && followed.maxY >= occupied.maxY
                && followed.minZ <= occupied.minZ && followed.maxZ >= occupied.maxZ);
        assertTrue(KineticThrow.edgeDistance(followed, occupied) >= 1.0);
        assertTrue(followed.getCenter().x - region.getCenter().x
                < occupied.getCenter().x - region.getCenter().x + 2.5 + 1.0e-9);
        assertNull(KineticThrow.followBounds(region, occupied, Vec3.ZERO, 0.5, 1.0, 0, 4, limits));
    }

    @Test
    void edgeDistanceIsNegativeWhenTheBoxLeavesTheRegion() {
        var region = new AABB(0, 0, 0, 10, 10, 10);

        assertEquals(2.0, KineticThrow.edgeDistance(region, new AABB(2, 2, 2, 8, 8, 8)), 1.0e-9);
        assertTrue(KineticThrow.edgeDistance(region, new AABB(-1, 2, 2, 8, 8, 8)) < 0);
    }
}
