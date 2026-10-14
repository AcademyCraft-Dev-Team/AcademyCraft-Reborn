package org.academy.internal.common.ability.electromaster.magnet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticStructureServiceTest {
    @Test
    void budgetCropKeepsTheSeedAndIsDeterministic() {
        var seed = new BlockPos(0, 64, 0);
        var positions = List.of(
                seed,
                seed.offset(3, 0, 0),
                seed.offset(1, 0, 0),
                seed.offset(2, 0, 0),
                seed.offset(1, 0, 0)
        );
        var cropped = MagneticStructureService.cropToBudget(positions, seed, 3);
        assertEquals(3, cropped.size());
        assertEquals(seed, cropped.getFirst());
        assertEquals(List.of(seed, seed.offset(1, 0, 0), seed.offset(2, 0, 0)), cropped);
        assertEquals(cropped, MagneticStructureService.cropToBudget(new ArrayList<>(positions.reversed()), seed, 3));
        assertTrue(MagneticStructureService.cropToBudget(positions, seed, 0).isEmpty());
    }

    @Test
    void regionBoundsCentreTheCapturedCellWithinTheHostLimits() {
        var limits = new AABB(-100.0, -64.0, -100.0, 100.0, 320.0, 100.0);
        var bounds = MagneticStructureService.regionBounds(
                List.of(new BlockPos(10, 100, 20)), -64.0, 320.0, limits);

        assertNotNull(bounds);
        assertEquals(13.0, bounds.getXsize(), 1.0e-9);
        assertEquals(13.0, bounds.getYsize(), 1.0e-9);
        assertEquals(13.0, bounds.getZsize(), 1.0e-9);
        assertTrue(bounds.minX <= 10.0 && bounds.maxX >= 11.0);
        assertTrue(bounds.minY <= 100.0 && bounds.maxY >= 101.0);
        assertTrue(bounds.minZ <= 20.0 && bounds.maxZ >= 21.0);
    }

    @Test
    void regionBoundsClampToHeightAndRejectUnusableHosts() {
        var limits = new AABB(-100.0, -64.0, -100.0, 100.0, 104.0, 100.0);
        var bounds = MagneticStructureService.regionBounds(
                List.of(new BlockPos(0, 100, 0)), -64.0, 104.0, limits);

        assertNotNull(bounds);
        assertEquals(104.0, bounds.maxY, 1.0e-9);
        assertEquals(13.0, bounds.getYsize(), 1.0e-9);
        assertNull(MagneticStructureService.regionBounds(List.of(), -64.0, 320.0, limits));
        assertNull(MagneticStructureService.regionBounds(
                List.of(new BlockPos(0, 100, 0)), 100.0, 104.0, limits));
    }

    @Test
    void followBoundsRecentersAheadAndRejectsUncontainableOccupancy() {
        var region = new AABB(0, 0, 0, 13, 13, 13);
        var occupied = new AABB(10, 2, 2, 14, 5, 5);
        var limits = new AABB(-100.0, -64.0, -100.0, 100.0, 320.0, 100.0);
        var followed = MagneticStructureService.followBounds(region, occupied, new Vec3(50, 0, 0), 0.5, 2.0,
                -64.0, 320.0, limits);

        assertNotNull(followed);
        assertEquals(13.0, followed.getXsize(), 1.0e-9);
        assertTrue(followed.minX <= occupied.minX && followed.maxX >= occupied.maxX
                && followed.minY <= occupied.minY && followed.maxY >= occupied.maxY
                && followed.minZ <= occupied.minZ && followed.maxZ >= occupied.maxZ);
        assertTrue(MagneticStructureService.edgeDistance(followed, occupied) >= 2.0);
        assertNull(MagneticStructureService.followBounds(region, occupied, Vec3.ZERO, 0.5, 2.0, 0.0, 4.0, limits));
    }

    @Test
    void edgeDistanceIsNegativeWhenTheBoxLeavesTheRegion() {
        var region = new AABB(0, 0, 0, 13, 13, 13);

        assertEquals(2.0, MagneticStructureService.edgeDistance(region, new AABB(2, 2, 2, 11, 11, 11)), 1.0e-9);
        assertTrue(MagneticStructureService.edgeDistance(region, new AABB(-1, 2, 2, 11, 11, 11)) < 0);
    }

    @Test
    void programLaunchSpeedMatchesTheNativeLegacyCalibrationAtEveryStrength() {
        assertEquals(14.0, MagneticStructureService.launchSpeed(0.5), 1.0e-9);
        assertEquals(28.0, MagneticStructureService.launchSpeed(1.0), 1.0e-9);
        assertEquals(42.0, MagneticStructureService.launchSpeed(1.5), 1.0e-9);
        assertThrows(IllegalArgumentException.class, () -> MagneticStructureService.launchSpeed(0.0));
        assertThrows(IllegalArgumentException.class, () -> MagneticStructureService.launchSpeed(2.5));
    }
}
