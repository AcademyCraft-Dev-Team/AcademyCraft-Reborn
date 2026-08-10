package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinearSegmentTest {
    @Test
    void exposesStableVectorOperations() {
        var segment = new LinearSegment(new Vec3(1, 2, 3), new Vec3(4, 6, 3));

        assertEquals(25.0, segment.lengthSqr(), 1.0E-9);
        assertEquals(5.0, segment.length(), 1.0E-9);
        var direction = segment.direction();
        assertEquals(0.6, direction.x, 1.0E-9);
        assertEquals(0.8, direction.y, 1.0E-9);
        assertEquals(0.0, direction.z, 1.0E-9);
        assertEquals(new Vec3(2.5, 4.0, 3.0), segment.pointAt(0.5));
        assertEquals(segment.end(), segment.reversed().start());
        assertEquals(segment.start(), segment.reversed().end());
        assertTrue(segment.isFinite());
    }

    @Test
    void clampsProgressAndRejectsDegenerateOrNonFiniteSegments() {
        var segment = new LinearSegment(Vec3.ZERO, new Vec3(10, 0, 0));

        assertEquals(Vec3.ZERO, segment.pointAt(-1.0));
        assertEquals(new Vec3(10, 0, 0), segment.pointAt(2.0));
        assertEquals(Vec3.ZERO, segment.pointAt(Double.NaN));
        var point = new LinearSegment(Vec3.ZERO, Vec3.ZERO);
        assertFalse(point.isFinite());
        assertTrue(point.hasFiniteCoordinates());
        var nonFinite = new LinearSegment(Vec3.ZERO, new Vec3(Double.NaN, 0, 0));
        assertFalse(nonFinite.isFinite());
        assertFalse(nonFinite.hasFiniteCoordinates());
    }

    @Test
    void degenerateSegmentsRemainDirectionlessAndStableUnderInterpolation() {
        var start = new Vec3(2, 3, 4);
        var point = new LinearSegment(start, start);
        var belowLengthThreshold = new LinearSegment(start, start.add(1.0E-7, 0.0, 0.0));

        assertEquals(Vec3.ZERO, point.direction());
        assertEquals(start, point.pointAt(0.75));
        assertEquals(start, point.pointAt(Double.POSITIVE_INFINITY));
        assertEquals(Vec3.ZERO, belowLengthThreshold.direction());
        assertFalse(belowLengthThreshold.isFinite());
    }

    @Test
    void limitsTheEndpointWithoutChangingTheReturnDirection() {
        var segment = new LinearSegment(new Vec3(10, 0, 0), new Vec3(-2, 0, 0));

        var blocked = segment.limitedTo(4.5);

        assertEquals(segment.start(), blocked.start());
        assertEquals(new Vec3(5.5, 0, 0), blocked.end());
        assertEquals(segment.direction(), blocked.direction());
        assertEquals(4.5, blocked.length(), 1.0E-9);
        assertEquals(segment, segment.limitedTo(20.0));
        assertEquals(segment, segment.limitedTo(Double.NaN));
        assertEquals(new LinearSegment(segment.start(), segment.start()), segment.limitedTo(-1.0));
    }
}
