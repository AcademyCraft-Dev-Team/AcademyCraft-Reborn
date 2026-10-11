package org.academy.api.common.vfx;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanarSheetOrientationTest {
    @Test
    void shaderBasisReconstructsDefaultVerticalAndArbitrarilyRolledBladeAxes() {
        for (var forward : new Vec3[]{new Vec3(0, 0, 1), new Vec3(0, 1, 0),
                new Vec3(1, 2, -3).normalize(), new Vec3(-2, -1, 4).normalize()}) {
            var base = Math.abs(forward.y) > 0.95 ? new Vec3(1, 0, 0)
                    : new Vec3(0, 1, 0).cross(forward).normalize();
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI / 12;
                var right = base.scale(Math.cos(angle)).add(forward.cross(base).scale(Math.sin(angle)));
                var normal = right.cross(forward);
                assertAxes(normal, right, forward);
                assertAxes(right, normal.scale(-1), forward);
            }
        }
    }

    private static void assertAxes(Vec3 normal, Vec3 expectedRight, Vec3 expectedAlong) {
        var reference = Math.abs(normal.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        var tangent = reference.cross(normal).normalize();
        var up = normal.cross(tangent);
        float roll = PlanarSheetOrientation.roll(normal, expectedRight);
        var actualRight = tangent.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
        assertEquals(0, actualRight.distanceTo(expectedRight), 0.000001);
        assertEquals(0, normal.cross(actualRight).distanceTo(expectedAlong), 0.000001);
    }
}
