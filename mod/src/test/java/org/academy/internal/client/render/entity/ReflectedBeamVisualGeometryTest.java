package org.academy.internal.client.render.entity;

import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.renderer.entity.ReflectedBeamVisualGeometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReflectedBeamVisualGeometryTest {
    private static final double EPSILON = 1.0e-9;

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }

    @Test
    void directionalReturnFollowsRefractionInsteadOfGoingBackToTheSource() {
        var reflectionPoint = new Vec3(1.0, 2.0, 3.0);
        var direction = new Vec3(1.0, 0.0, 1.0).normalize();

        var returnEnd = ReflectedBeamVisualGeometry.directionalEnd(
                reflectionPoint, direction, 8.0f
        );

        assertVecEquals(direction, returnEnd.subtract(reflectionPoint).normalize());
        assertEquals(8.0, returnEnd.distanceTo(reflectionPoint), EPSILON);
    }
}
