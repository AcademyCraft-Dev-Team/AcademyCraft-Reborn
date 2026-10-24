package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.upgrade.StructureExtraction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionControllerTest {
    private static final Vec3 GRAVITY = new Vec3(0, -9.81, 0);
    private static StructureExtraction.Limits limits(double force) {
        return new StructureExtraction.Limits(8, 40, force, 2, 4096);
    }

    @Test void hoverCompensationConsumesRealForceBudget() {
        var step = ExtractionController.step(Vec3.ZERO, Vec3.ZERO, GRAVITY, 10, Vec3.ZERO, limits(1000), .025);
        assertEquals(98.1, step.force(), 1E-10);
        assertEquals(0, step.displacement().length(), 1E-10);
        assertTrue(step.stable());
        var insufficient = ExtractionController.step(Vec3.ZERO, Vec3.ZERO, GRAVITY, 10, new Vec3(0, 3, 0), limits(50), .025);
        assertEquals(50, insufficient.force(), 1E-10);
        assertTrue(insufficient.displacement().y < 0);
        assertFalse(insufficient.stable());
    }

    @Test void overspeedIsBrakedWithinForceAndAccelerationWithoutTeleporting() {
        var velocity = new Vec3(40, 0, 0);
        var step = ExtractionController.step(Vec3.ZERO, velocity, Vec3.ZERO, 10, Vec3.ZERO, limits(20), .05);
        assertEquals(39.9, step.velocity().x, 1E-10);
        assertEquals(20, step.force(), 1E-9);
        assertFalse(step.stable());
    }

    @Test void allThreeSourceRelativeHeightsConvergeWithGravityAndStayStableFor200Ticks() {
        for (double height : new double[]{3, 5, 7}) {
            var source = new Vec3(-156.5, 120.5, 61.5);
            var center = source;
            var target = source.add(0, height, 0);
            var velocity = Vec3.ZERO;
            int stableSteps = 0;
            for (int stepIndex = 0; stepIndex < 3200 && stableSteps < 800; stepIndex++) {
                var step = ExtractionController.step(center, velocity, GRAVITY, 4, target, limits(1000), .0125);
                assertTrue(step.force() <= 160 + 1E-8);
                stableSteps = step.stable() ? stableSteps + 1 : 0;
                velocity = step.velocity().add(GRAVITY.scale(.0125));
                center = center.add(velocity.scale(.0125));
            }
            assertEquals(800, stableSteps, "height=" + height);
            assertEquals(height, center.y - source.y, ExtractionController.POSITION_TOLERANCE);
        }
    }

    @Test void substepsDoNotMultiplyAvailableImpulse() {
        var full = ExtractionController.step(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 10, new Vec3(100, 0, 0), limits(20), .05);
        var velocity = Vec3.ZERO;
        double impulse = 0;
        for (int index = 0; index < 4; index++) {
            var step = ExtractionController.step(Vec3.ZERO, velocity, Vec3.ZERO, 10, new Vec3(100, 0, 0), limits(20), .0125);
            impulse += step.force() * .0125;
            velocity = step.velocity();
        }
        assertEquals(full.velocity().x, velocity.x, 1E-10);
        assertEquals(1, impulse, 1E-10);
    }

    @Test void aLateralShoveIsBrakedBeforeSpendingRemainingForceOnLift() {
        var velocity = new Vec3(.2, 2, .05);
        var step = ExtractionController.step(Vec3.ZERO, velocity, GRAVITY, 128,
                new Vec3(0, 3, 0), limits(10_000), .025);
        assertEquals(0, step.velocity().x, 1E-12);
        assertEquals(0, step.velocity().z, 1E-12);
        assertTrue(step.velocity().y > velocity.y);
        assertTrue(step.force() <= 128 * 40 + 1E-8);
        assertEquals(1, step.velocity().subtract(velocity).length(), 1E-12);
    }

    @Test void anUnstoppableLateralShoveUsesTheBudgetWithoutUnboundedVelocityReset() {
        var velocity = new Vec3(10, 0, 0);
        var step = ExtractionController.step(Vec3.ZERO, velocity, GRAVITY, 128,
                new Vec3(0, 3, 0), limits(10_000), .025);
        assertEquals(9, step.velocity().x, 1E-12);
        assertEquals(0, step.velocity().y, 1E-12);
        assertEquals(128 * 40, step.force(), 1E-8);
    }

    @Test void vegetationLookAheadCoversNextTickWithoutRemovingPastTheWaypoint() {
        var source = new Vec3(-156, 123, 61);
        var future = ExtractionController.clearanceDisplacement(source, source.add(0, 3, 0), Vec3.ZERO, limits(1000));
        assertEquals(new Vec3(0, .5, 0), future);
        var shortMove = ExtractionController.clearanceDisplacement(source, source.add(0, .02, 0), Vec3.ZERO, limits(1000));
        assertEquals(.02, shortMove.y, 1E-10);
        assertEquals(Vec3.ZERO, ExtractionController.clearanceDisplacement(source, source, Vec3.ZERO, limits(1000)));
        var overspeed = ExtractionController.clearanceDisplacement(source, source.add(3, 0, 0), new Vec3(12, 0, 0), limits(1000));
        assertEquals(.7, overspeed.x, 1E-10);
        assertEquals(0, overspeed.y);
    }

    @Test void rejectsInvalidObservationRatherThanPublishingNonfiniteCommands() {
        assertThrows(IllegalArgumentException.class, () -> ExtractionController.step(Vec3.ZERO, Vec3.ZERO,
                GRAVITY, 0, Vec3.ZERO, limits(1000), .025));
        assertThrows(IllegalArgumentException.class, () -> ExtractionController.step(Vec3.ZERO, Vec3.ZERO,
                GRAVITY, 1, Vec3.ZERO, limits(1000), .1));
    }
}
