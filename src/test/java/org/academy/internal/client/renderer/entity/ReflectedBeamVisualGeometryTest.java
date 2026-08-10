package org.academy.internal.client.renderer.entity;

import net.minecraft.world.phys.Vec3;
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
    void fullReturnExtendsPastOriginByUntraveledLength() {
        var reflectionPoint = new Vec3(0.0, 0.0, 4.0);

        var returnEnd = ReflectedBeamVisualGeometry.fullReturnEnd(
                reflectionPoint, new Vec3(0.0, 0.0, 1.0), 10.0f
        );

        assertVecEquals(new Vec3(0.0, 0.0, -6.0), returnEnd);
        assertEquals(10.0, returnEnd.distanceTo(reflectionPoint), EPSILON);
    }

    @Test
    void returnUsesTheAuthoritativeLogicalDirection() {
        var reflectionPoint = new Vec3(0.0, 0.0, 4.0);
        var logicalDirection = new Vec3(0.0, 0.0, 1.0);

        var returnEnd = ReflectedBeamVisualGeometry.fullReturnEnd(
                reflectionPoint, logicalDirection, 10.0f
        );
        var returnDirection = returnEnd.subtract(reflectionPoint).normalize();

        assertVecEquals(logicalDirection.scale(-1.0), returnDirection);
        assertEquals(10.0, returnEnd.distanceTo(reflectionPoint), EPSILON);
    }

    @Test
    void zeroDistanceReflectionUsesBackwardLogicalDirection() {
        var returnEnd = ReflectedBeamVisualGeometry.fullReturnEnd(
                Vec3.ZERO, new Vec3(0.0, 0.0, 1.0), 10.0f
        );

        assertVecEquals(new Vec3(0.0, 0.0, -10.0), returnEnd);
    }

    @Test
    void invalidOrNegativeLengthsProduceNoReturn() {
        var reflectionPoint = new Vec3(0.0, 0.0, 4.0);

        assertVecEquals(reflectionPoint, ReflectedBeamVisualGeometry.fullReturnEnd(
                reflectionPoint, new Vec3(0.0, 0.0, 1.0), -1.0f
        ));
        assertVecEquals(reflectionPoint, ReflectedBeamVisualGeometry.fullReturnEnd(
                reflectionPoint, new Vec3(0.0, 0.0, 1.0), Float.NaN
        ));
        assertVecEquals(reflectionPoint, ReflectedBeamVisualGeometry.fullReturnEnd(
                reflectionPoint, Vec3.ZERO, 10.0f
        ));
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
