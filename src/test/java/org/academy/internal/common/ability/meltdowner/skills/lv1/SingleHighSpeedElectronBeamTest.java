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
    void fullProficiencyAimsFromRandomizedOriginAtCrosshairPoint() {
        var forward = new Vec3(0.0, 0.0, 1.0);
        var randomizedOrigin = new Vec3(1.25, -0.4, 2.0);
        var crosshairPoint = new Vec3(0.0, 0.0, 30.0);

        var direction = SingleHighSpeedElectronBeam.getCorrectedAimDirection(
                forward, randomizedOrigin, crosshairPoint, 3000.0f);
        var expected = crosshairPoint.subtract(randomizedOrigin).normalize();

        assertEquals(expected.x, direction.x, EPSILON);
        assertEquals(expected.y, direction.y, EPSILON);
        assertEquals(expected.z, direction.z, EPSILON);
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
