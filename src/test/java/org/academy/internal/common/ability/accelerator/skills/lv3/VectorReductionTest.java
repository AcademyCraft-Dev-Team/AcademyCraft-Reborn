package org.academy.internal.common.ability.accelerator.skills.lv3;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorReductionTest {
    @Test
    void refractedDirectionFormsSixtyDegreeAngleWithView() {
        var result = VectorReduction.refractedDirection(
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(1.0, 0.0, 0.0)
        );

        assertEquals(1.0, result.length(), 0.0001);
        assertEquals(0.5, result.dot(new Vec3(0.0, 0.0, 1.0)), 0.0001);
    }

    @Test
    void upwardRefractionIsProjectedDownToThirtyDegrees() {
        var result = VectorReduction.refractedDirection(
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(1.0, 1.0, 0.0)
        );

        assertEquals(1.0, result.length(), 0.0001);
        assertEquals(0.5, result.y, 0.0001);
    }

    @Test
    void downwardRefractionIsProjectedOntoTheHorizontalPlane() {
        var result = VectorReduction.refractedDirection(
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
            var result = VectorReduction.refractedDirection(new Vec3(0.0, 0.0, 1.0), incoming);
            assertEquals(1.0, result.length(), 0.0001);
            assertTrue(result.y >= -0.0001);
            assertTrue(result.y <= 0.5001);
        }
    }
}
