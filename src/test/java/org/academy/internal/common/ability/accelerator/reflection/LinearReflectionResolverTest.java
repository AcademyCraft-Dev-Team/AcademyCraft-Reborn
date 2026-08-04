package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinearReflectionResolverTest {
    @Test
    void findsHorizontalAndVerticalSlabEntries() {
        var horizontal = LinearReflectionResolver.intersectionProgress(
                Vec3.ZERO,
                new Vec3(10, 0, 0),
                new AABB(4, -1, -1, 6, 1, 1)
        );
        var vertical = LinearReflectionResolver.intersectionProgress(
                new Vec3(0, -10, 0),
                new Vec3(0, 10, 0),
                new AABB(-1, -2, -1, 1, 2, 1)
        );

        assertTrue(horizontal.isPresent());
        assertEquals(0.4, horizontal.getAsDouble(), 1.0E-9);
        assertTrue(vertical.isPresent());
        assertEquals(0.4, vertical.getAsDouble(), 1.0E-9);
    }

    @Test
    void rejectsParallelMissesAndDegenerateInputs() {
        assertTrue(LinearReflectionResolver.intersectionProgress(
                Vec3.ZERO,
                new Vec3(10, 0, 0),
                new AABB(2, 2, -1, 4, 4, 1)
        ).isEmpty());
        assertTrue(LinearReflectionResolver.intersectionProgress(
                Vec3.ZERO,
                Vec3.ZERO,
                new AABB(-1, -1, -1, 1, 1, 1)
        ).isEmpty());
        assertTrue(LinearReflectionResolver.intersectionProgress(
                Vec3.ZERO,
                new Vec3(Double.NaN, 0, 0),
                new AABB(-1, -1, -1, 1, 1, 1)
        ).isEmpty());
    }

    @Test
    void returnsZeroWhenTheRayStartsInsideTheBounds() {
        var progress = LinearReflectionResolver.intersectionProgress(
                Vec3.ZERO,
                new Vec3(0, 0, 10),
                new AABB(-1, -1, -1, 1, 1, 1)
        );

        assertTrue(progress.isPresent());
        assertEquals(0.0, progress.getAsDouble(), 1.0E-9);
    }

    @Test
    void projectsVisualMirrorPointForRadiusOnlyHits() {
        assertEquals(0.5, LinearReflectionResolver.projectedProgress(
                Vec3.ZERO,
                new Vec3(10, 0, 0),
                new Vec3(5, 2, 0)
        ), 1.0E-9);
        assertEquals(0.0, LinearReflectionResolver.projectedProgress(
                Vec3.ZERO,
                new Vec3(10, 0, 0),
                new Vec3(-5, 2, 0)
        ), 1.0E-9);
        assertEquals(1.0, LinearReflectionResolver.projectedProgress(
                Vec3.ZERO,
                new Vec3(10, 0, 0),
                new Vec3(15, 2, 0)
        ), 1.0E-9);
    }

    @Test
    void fullRangeReturnSegmentContinuesPastTheOriginalStart() {
        var original = new LinearSegment(Vec3.ZERO, new Vec3(10, 0, 0));
        var returned = LinearReflectionResolver.fullRangeReturnSegment(
                original,
                new Vec3(4, 0, 0)
        ).orElseThrow();

        assertVecEquals(new Vec3(4 - LinearReflectionResolver.RETURN_EPSILON, 0, 0), returned.start());
        assertVecEquals(new Vec3(-6, 0, 0), returned.end());
        assertVecEquals(new Vec3(-1, 0, 0), returned.direction());
        assertEquals(10 - LinearReflectionResolver.RETURN_EPSILON, returned.length(), 1.0E-9);
    }

    @Test
    void fullRangeReturnSupportsDiagonalAndVerticalSegments() {
        var diagonal = new LinearSegment(new Vec3(1, 2, 3), new Vec3(4, 6, 3));
        var diagonalMirror = diagonal.pointAt(0.4);
        var diagonalReturn = LinearReflectionResolver.fullRangeReturnSegment(
                diagonal,
                diagonalMirror
        ).orElseThrow();
        var vertical = new LinearSegment(new Vec3(2, -3, 4), new Vec3(2, 7, 4));
        var verticalMirror = vertical.pointAt(0.25);
        var verticalReturn = LinearReflectionResolver.fullRangeReturnSegment(
                vertical,
                verticalMirror
        ).orElseThrow();

        assertVecEquals(new Vec3(-0.8, -0.4, 3), diagonalReturn.end());
        assertVecEquals(diagonal.direction().scale(-1), diagonalReturn.direction());
        assertEquals(diagonal.length(), diagonalMirror.distanceTo(diagonalReturn.end()), 1.0E-9);
        assertVecEquals(new Vec3(2, -10.5, 4), verticalReturn.end());
        assertVecEquals(new Vec3(0, -1, 0), verticalReturn.direction());
        assertEquals(vertical.length(), verticalMirror.distanceTo(verticalReturn.end()), 1.0E-9);
    }

    @Test
    void fullRangeReturnHandlesZeroAndFullReflectionProgress() {
        var original = new LinearSegment(Vec3.ZERO, new Vec3(10, 0, 0));
        var atStart = LinearReflectionResolver.fullRangeReturnSegment(
                original,
                original.pointAt(0.0)
        ).orElseThrow();
        var atEnd = LinearReflectionResolver.fullRangeReturnSegment(
                original,
                original.pointAt(1.0)
        ).orElseThrow();

        assertVecEquals(Vec3.ZERO, atStart.start());
        assertVecEquals(new Vec3(-10, 0, 0), atStart.end());
        assertEquals(10, atStart.length(), 1.0E-9);
        assertVecEquals(new Vec3(10 - LinearReflectionResolver.RETURN_EPSILON, 0, 0), atEnd.start());
        assertVecEquals(Vec3.ZERO, atEnd.end());
        assertEquals(10 - LinearReflectionResolver.RETURN_EPSILON, atEnd.length(), 1.0E-9);
    }

    @Test
    void shortReturnsDoNotApplyAnOversizedEpsilon() {
        var length = LinearReflectionResolver.RETURN_EPSILON * 0.5;
        var original = new LinearSegment(Vec3.ZERO, new Vec3(length, 0, 0));
        var returned = LinearReflectionResolver.fullRangeReturnSegment(
                original,
                original.end()
        ).orElseThrow();

        assertVecEquals(original.end(), returned.start());
        assertVecEquals(original.start(), returned.end());
        assertEquals(length, returned.length(), 1.0E-12);
    }

    @Test
    void fullRangeReturnRejectsDegenerateAndNonFiniteInputs() {
        var valid = new LinearSegment(Vec3.ZERO, new Vec3(10, 0, 0));

        assertTrue(LinearReflectionResolver.fullRangeReturnSegment(
                new LinearSegment(Vec3.ZERO, Vec3.ZERO),
                Vec3.ZERO
        ).isEmpty());
        assertTrue(LinearReflectionResolver.fullRangeReturnSegment(
                new LinearSegment(Vec3.ZERO, new Vec3(Double.NaN, 0, 0)),
                Vec3.ZERO
        ).isEmpty());
        assertTrue(LinearReflectionResolver.fullRangeReturnSegment(
                valid,
                new Vec3(0, Double.POSITIVE_INFINITY, 0)
        ).isEmpty());
        assertFalse(LinearReflectionResolver.fullRangeReturnSegment(valid, Vec3.ZERO).isEmpty());
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-9);
        assertEquals(expected.y, actual.y, 1.0E-9);
        assertEquals(expected.z, actual.z, 1.0E-9);
    }
}
