package org.academy.internal.common.ability.aeromanip.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighSpeedJetStructureServiceTest {
    @Test
    void impactRequiresClosingSpeedAndUsesBoundedDamage() {
        assertEquals(0.0f,
                HighSpeedJetStructureService.impactDamage(4, 0.34, 1.0f));
        assertEquals(2.4f,
                HighSpeedJetStructureService.impactDamage(4, 1.0, 1.0f),
                1.0e-6f);
        assertEquals(24.0f,
                HighSpeedJetStructureService.impactDamage(256, 100.0, 1.0f),
                1.0e-6f);
    }

    @Test
    void knockbackUsesTheSameImpactGateAndCap() {
        assertEquals(0.0,
                HighSpeedJetStructureService.knockbackStrength(4, 0.34, 1.0f));
        assertEquals(0.64,
                HighSpeedJetStructureService.knockbackStrength(4, 1.0, 1.0f),
                1.0e-9);
        assertEquals(2.4,
                HighSpeedJetStructureService.knockbackStrength(256, 100.0, 1.0f),
                1.0e-9);
    }

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
        var cropped = HighSpeedJetStructureService.cropToBudget(positions, seed, 3);
        assertEquals(3, cropped.size());
        assertEquals(seed, cropped.getFirst());
        assertEquals(List.of(seed, seed.offset(1, 0, 0), seed.offset(2, 0, 0)), cropped);
        assertTrue(HighSpeedJetStructureService.cropToBudget(positions, seed, 0).isEmpty());
    }

    @Test
    void regionBoundsCentersTheLaunchAxisWithinItsLimit() {
        var limits = new AABB(-100.0, -64.0, -100.0, 100.0, 320.0, 100.0);
        var bounds = HighSpeedJetStructureService.regionBounds(
                List.of(new BlockPos(0, 64, 0)), new Vec3(0.0, 0.0, 1.0), -64.0, 320.0, limits);
        assertTrue(bounds != null);
        assertEquals(24.0, bounds.getZsize(), 1.0e-9);
        assertTrue(bounds.minX < 0.5 && bounds.maxX > 0.5);
        assertTrue(bounds.minY < 64.5 && bounds.maxY > 64.5);
        assertTrue(bounds.minZ < 0.0 && bounds.maxZ > 1.0);
    }

    @Test
    void regionBoundsKeepsTheStructureInsideTheAvailableHostRoom() {
        var limits = new AABB(-100.0, -64.0, -100.0, 100.0, 320.0, 10.0);
        var bounds = HighSpeedJetStructureService.regionBounds(
                List.of(new BlockPos(0, 64, 0)), new Vec3(0.0, 0.0, 1.0), -64.0, 320.0, limits);
        assertTrue(bounds != null);
        assertEquals(10.0, bounds.maxZ, 1.0e-9);
        assertTrue(bounds.minZ <= 0.0);
        assertTrue(bounds.getZsize() <= 24.0);
        assertNull(HighSpeedJetStructureService.regionBounds(
                List.of(), new Vec3(0.0, 0.0, 1.0), -64.0, 320.0, limits));
    }
}
