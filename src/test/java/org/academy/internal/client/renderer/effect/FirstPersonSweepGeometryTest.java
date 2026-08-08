package org.academy.internal.client.renderer.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstPersonSweepGeometryTest {
    @Test
    void wingProjectionsAreMirroredAndStayInFrontOfCamera() {
        for (var i = 0; i <= 20; i++) {
            var progress = i / 20.0f;
            var left = FirstPersonSweepGeometry.wingProjection(true, progress);
            var right = FirstPersonSweepGeometry.wingProjection(false, progress);

            assertEquals(-right.rootX(), left.rootX(), 0.0001f);
            assertEquals(right.rootY(), left.rootY(), 0.0001f);
            assertEquals(right.rootZ(), left.rootZ(), 0.0001f);
            assertEquals(-right.sweepDegrees(), left.sweepDegrees(), 0.0001f);

            for (var j = 0; j <= 10; j++) {
                var leftCenter = left.centerline(j / 10.0f);
                var rightCenter = right.centerline(j / 10.0f);
                assertEquals(-rightCenter.x(), leftCenter.x(), 0.0001f);
                assertEquals(rightCenter.y(), leftCenter.y(), 0.0001f);
                assertEquals(rightCenter.z(), leftCenter.z(), 0.0001f);
                assertTrue(leftCenter.z() <= -0.78f);
            }
        }
    }

    @Test
    void wingProjectionFadesAtLifetimeBoundaries() {
        assertEquals(0.0f, FirstPersonSweepGeometry.wingProjection(true, 0.0f).alpha(), 0.0001f);
        assertEquals(0.0f, FirstPersonSweepGeometry.wingProjection(true, 1.0f).alpha(), 0.0001f);
        assertTrue(FirstPersonSweepGeometry.wingProjection(true, 0.5f).alpha() > 0.0f);
    }

    @Test
    void ironSandSweepMirrorsMainArmAndStaysInFrontOfCamera() {
        for (var i = 0; i < FirstPersonSweepGeometry.IRON_SAND_PARTICLES; i++) {
            var right = FirstPersonSweepGeometry.ironSandPosition(1.0f, 0.5f, i);
            var left = FirstPersonSweepGeometry.ironSandPosition(-1.0f, 0.5f, i);

            assertEquals(-right.x(), left.x(), 0.0001f);
            assertEquals(right.y(), left.y(), 0.0001f);
            assertEquals(right.z(), left.z(), 0.0001f);
            assertTrue(right.z() <= -0.55f && right.z() >= -4.0f);
            assertEquals(0.0f, FirstPersonSweepGeometry.ironSandAlpha(0.0f, i), 0.0001f);
            assertEquals(0.0f, FirstPersonSweepGeometry.ironSandAlpha(1.0f, i), 0.0001f);
        }
    }
}
