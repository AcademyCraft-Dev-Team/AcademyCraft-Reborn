package org.academy.internal.common.ability.meltdowner.skills.lv1;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleHighSpeedElectronBeamTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    void aimCorrectionImprovesContinuouslyWithProficiency() {
        assertEquals(0.5f, SingleHighSpeedElectronBeam.getAimCorrection(0.0f));
        assertEquals(2.0f / 3.0f,
                SingleHighSpeedElectronBeam.getAimCorrection(1000.0f), 1.0E-6f);
        assertEquals(5.0f / 6.0f,
                SingleHighSpeedElectronBeam.getAimCorrection(2000.0f), 1.0E-6f);
        assertEquals(1.0f, SingleHighSpeedElectronBeam.getAimCorrection(3000.0f));
        assertEquals(0.5f, SingleHighSpeedElectronBeam.getAimCorrection(-100.0f));
        assertEquals(1.0f, SingleHighSpeedElectronBeam.getAimCorrection(4000.0f));
    }

    @Test
    void fullProficiencyAimsFromConstrainedRandomOriginAtCrosshairPoint() {
        var forward = new Vec3(0.0, 0.0, 1.0);
        var viewOrigin = Vec3.ZERO;
        var randomizedOrigin = new Vec3(1.25, -0.4, 2.0);
        var crosshairPoint = new Vec3(0.0, 0.0, 30.0);
        var constrainedOrigin = SingleHighSpeedElectronBeam.constrainRandomSpawnPosition(
                viewOrigin, forward, randomizedOrigin, crosshairPoint);

        var direction = SingleHighSpeedElectronBeam.getCorrectedAimDirection(
                forward, constrainedOrigin, crosshairPoint, 3000.0f);
        var expected = crosshairPoint.subtract(constrainedOrigin).normalize();

        assertEquals(expected.x, direction.x, EPSILON);
        assertEquals(expected.y, direction.y, EPSILON);
        assertEquals(expected.z, direction.z, EPSILON);
    }

    @Test
    void closeTargetTightensOriginToForwardDestructionCone() {
        var forward = new Vec3(0.0, 0.0, 1.0);
        var viewOrigin = Vec3.ZERO;
        var randomizedOrigin = new Vec3(1.25, -0.4, 2.0);
        var closeTarget = new Vec3(0.0, 0.0, 1.0);

        var constrainedOrigin = SingleHighSpeedElectronBeam.constrainRandomSpawnPosition(
                viewOrigin, forward, randomizedOrigin, closeTarget);
        var direction = SingleHighSpeedElectronBeam.getCorrectedAimDirection(
                forward, constrainedOrigin, closeTarget, 3000.0f);
        var minimumForwardDot = Math.cos(Math.toRadians(
                SingleHighSpeedElectronBeam.MAX_AIM_DEVIATION_DEGREES));

        assertTrue(direction.dot(forward) >= minimumForwardDot - EPSILON);
        assertTrue(constrainedOrigin.subtract(viewOrigin).dot(forward)
                < closeTarget.subtract(viewOrigin).dot(forward));
        assertTrue(constrainedOrigin.distanceTo(randomizedOrigin) > 0.1);
    }

    @Test
    void distantTargetKeepsSafeRandomOriginUnchanged() {
        var forward = new Vec3(0.0, 0.0, 1.0);
        var viewOrigin = Vec3.ZERO;
        var randomizedOrigin = new Vec3(1.25, -0.4, 2.0);
        var distantTarget = new Vec3(0.0, 0.0, 50.0);

        var constrainedOrigin = SingleHighSpeedElectronBeam.constrainRandomSpawnPosition(
                viewOrigin, forward, randomizedOrigin, distantTarget);

        assertEquals(randomizedOrigin.x, constrainedOrigin.x, EPSILON);
        assertEquals(randomizedOrigin.y, constrainedOrigin.y, EPSILON);
        assertEquals(randomizedOrigin.z, constrainedOrigin.z, EPSILON);
    }

    @Test
    void initialCorrectionIsMoreAccurateThanParallelFire() {
        var forward = new Vec3(0.0, 0.0, 1.0);
        var randomizedOrigin = new Vec3(1.25, -0.4, 2.0);
        var crosshairPoint = new Vec3(0.0, 0.0, 30.0);
        var perfect = crosshairPoint.subtract(randomizedOrigin).normalize();

        var direction = SingleHighSpeedElectronBeam.getCorrectedAimDirection(
                forward, randomizedOrigin, crosshairPoint, 0.0f);

        assertTrue(direction.dot(perfect) > forward.dot(perfect));
        assertTrue(direction.dot(perfect) < 1.0);
    }
}
