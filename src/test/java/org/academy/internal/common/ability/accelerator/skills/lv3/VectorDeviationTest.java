package org.academy.internal.common.ability.accelerator.skills.lv3;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorDeviationTest {
    @Test
    void incompleteProficiencyLimitsNegativeHealthWritesByMilestone() {
        assertEquals(15.0f,
                VectorDeviation.Server.limitNegativeHealthChange(20.0f, 10.0f, 0),
                0.0001f);
        assertEquals(17.0f,
                VectorDeviation.Server.limitNegativeHealthChange(20.0f, 10.0f, 1),
                0.0001f);
        assertEquals(19.0f,
                VectorDeviation.Server.limitNegativeHealthChange(20.0f, 10.0f, 2),
                0.0001f);
    }

    @Test
    void fullProficiencyAndHealingKeepTheRequestedHealthWrite() {
        assertEquals(10.0f,
                VectorDeviation.Server.limitNegativeHealthChange(20.0f, 10.0f, 3),
                0.0001f);
        assertEquals(20.0f,
                VectorDeviation.Server.limitNegativeHealthChange(10.0f, 20.0f, 0),
                0.0001f);
    }

    @Test
    void lowProficiencyRefractionUsesAStableFiftyPercentBoundary() {
        assertTrue(VectorDeviation.Server.passesLowProficiencyRefractionRoll(0.0f));
        assertTrue(VectorDeviation.Server.passesLowProficiencyRefractionRoll(0.499999f));
        assertFalse(VectorDeviation.Server.passesLowProficiencyRefractionRoll(0.5f));
        assertFalse(VectorDeviation.Server.passesLowProficiencyRefractionRoll(0.999999f));
        assertFalse(VectorDeviation.Server.passesLowProficiencyRefractionRoll(Float.NaN));
    }

    @Test
    void refractedDirectionFormsSixtyDegreeAngleWithView() {
        var result = VectorDeviation.refractedDirection(
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(1.0, 0.0, 0.0)
        );

        assertEquals(1.0, result.length(), 0.0001);
        assertEquals(0.5, result.dot(new Vec3(0.0, 0.0, 1.0)), 0.0001);
    }

    @Test
    void upwardRefractionIsProjectedDownToThirtyDegrees() {
        var result = VectorDeviation.refractedDirection(
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(1.0, 1.0, 0.0)
        );

        assertEquals(1.0, result.length(), 0.0001);
        assertEquals(0.5, result.y, 0.0001);
    }

    @Test
    void downwardRefractionIsProjectedOntoTheHorizontalPlane() {
        var result = VectorDeviation.refractedDirection(
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(1.0, -1.0, 0.0)
        );

        assertEquals(1.0, result.length(), 0.0001);
        assertEquals(0.0, result.y, 0.0001);
    }

    @Test
    void everyRefractionStaysInsideTheUpwardElevationBand() {
        var directions = new Vec3[]{
                new Vec3(1.0, 4.0, 0.0),
                new Vec3(1.0, -4.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, -1.0, 0.0)
        };

        for (var incoming : directions) {
            var result = VectorDeviation.refractedDirection(new Vec3(0.0, 0.0, 1.0), incoming);
            assertEquals(1.0, result.length(), 0.0001);
            assertTrue(result.y >= -0.0001);
            assertTrue(result.y <= 0.5001);
        }
    }
}
