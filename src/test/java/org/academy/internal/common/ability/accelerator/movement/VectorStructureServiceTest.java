package org.academy.internal.common.ability.accelerator.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorStructureServiceTest {
    @Test
    void cropToBudgetKeepsSeedAndNearestCellsDeterministically() {
        var seed = new BlockPos(0, 0, 0);
        var positions = List.of(seed, new BlockPos(5, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0),
                new BlockPos(-3, 0, 0), new BlockPos(0, 4, 0), new BlockPos(1, 0, 0));
        var cropped = VectorStructureService.cropToBudget(positions, seed, 3);

        assertEquals(List.of(seed, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)), cropped);
        assertEquals(cropped, VectorStructureService.cropToBudget(new ArrayList<>(
                positions.reversed()), seed, 3));
        assertEquals(List.of(seed), VectorStructureService.cropToBudget(
                List.of(new BlockPos(9, 9, 9), seed, seed), seed, 1));
        assertTrue(VectorStructureService.cropToBudget(positions, seed, 0).isEmpty());
    }

    @Test
    void regionBoundsCoverStructureAndLiftWithinHostLimits() {
        var positions = List.of(new BlockPos(10, 100, 20), new BlockPos(12, 100, 22));
        var limits = new AABB(-100, -64, -100, 100, 320, 100);
        var bounds = VectorStructureService.regionBounds(positions, 4, -64, 320, limits);

        assertNotNull(bounds);
        assertEquals(15.0, bounds.getXsize(), 1.0e-9);
        assertEquals(17.0, bounds.getYsize(), 1.0e-9);
        assertEquals(15.0, bounds.getZsize(), 1.0e-9);
        assertTrue(bounds.minY <= 100 && bounds.maxY >= 105);
        assertNull(VectorStructureService.regionBounds(positions, 4, 100, 104, limits));
    }

    @Test
    void followBoundsRecentersAheadAndRejectsUncontainableOccupancy() {
        var region = new AABB(0, 0, 0, 10, 10, 10);
        var occupied = new AABB(8, 2, 2, 11, 5, 5);
        var limits = new AABB(-100, -64, -100, 100, 320, 100);
        var followed = VectorStructureService.followBounds(region, occupied, new Vec3(50, 0, 0), 0.5, 1.0,
                -64, 320, limits);

        assertNotNull(followed);
        assertEquals(region.getXsize(), followed.getXsize(), 1.0e-9);
        assertEquals(region.getYsize(), followed.getYsize(), 1.0e-9);
        assertTrue(followed.minX <= occupied.minX && followed.maxX >= occupied.maxX
                && followed.minY <= occupied.minY && followed.maxY >= occupied.maxY
                && followed.minZ <= occupied.minZ && followed.maxZ >= occupied.maxZ);
        assertTrue(VectorStructureService.edgeDistance(followed, occupied) >= 1.0);
        assertTrue(followed.getCenter().x - region.getCenter().x
                < occupied.getCenter().x - region.getCenter().x + 2.5 + 1.0e-9);
        assertNull(VectorStructureService.followBounds(region, occupied, Vec3.ZERO, 0.5, 1.0, 0, 4, limits));
    }

    @Test
    void edgeDistanceIsNegativeWhenTheBoxLeavesTheRegion() {
        var region = new AABB(0, 0, 0, 10, 10, 10);

        assertEquals(2.0, VectorStructureService.edgeDistance(region, new AABB(2, 2, 2, 8, 8, 8)), 1.0e-9);
        assertTrue(VectorStructureService.edgeDistance(region, new AABB(-1, 2, 2, 8, 8, 8)) < 0);
    }

    @Test
    void hoverDeadlineStartsTheFullHoldWindowWhenLiftCompletes() {
        assertEquals(200, KineticThrow.HOVER_TICKS);
        assertEquals(KineticThrow.HOVER_TICKS, VectorStructureService.hoverDeadline(0));
        assertEquals(1234L + KineticThrow.HOVER_TICKS, VectorStructureService.hoverDeadline(1234));
    }
}
