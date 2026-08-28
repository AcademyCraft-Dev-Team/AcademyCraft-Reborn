package org.academy.internal.common.ability.teleport;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpacialExcisionMathTest {
    private static final double EPSILON = 1.0e-6;

    @Test
    void horizontalTeleportUsesWorldUpForThePlaneBasis() {
        var basis = SpacialExcisionMath.planeBasis(Vec3.ZERO, new Vec3(0.0, 0.0, 10.0), 0.0f)
                .orElseThrow();

        assertVec(new Vec3(0.0, 0.0, 1.0), basis.tangent());
        assertVec(new Vec3(0.0, 1.0, 0.0), basis.planeUp());
        assertEquals(0.0, basis.tangent().dot(basis.planeUp()), EPSILON);
        assertTrue(SpacialExcisionMath.isFinite(basis.planeNormal()));
    }

    @Test
    void nearVerticalTeleportUsesThePreTeleportHorizontalRightAxis() {
        var basis = SpacialExcisionMath.planeBasis(Vec3.ZERO, new Vec3(0.0, 10.0, 0.0), 0.0f)
                .orElseThrow();

        assertVec(new Vec3(0.0, 1.0, 0.0), basis.tangent());
        assertVec(new Vec3(1.0, 0.0, 0.0), basis.planeUp());
        assertEquals(0.0, basis.tangent().dot(basis.planeUp()), EPSILON);
        assertEquals(1.0, basis.planeNormal().length(), EPSILON);
    }

    @Test
    void degenerateAndNonFiniteSegmentsAreRejected() {
        assertEquals(Optional.empty(), SpacialExcisionMath.planeBasis(Vec3.ZERO, Vec3.ZERO, 0.0f));
        assertEquals(Optional.empty(), SpacialExcisionMath.planeBasis(
                new Vec3(Double.NaN, 0.0, 0.0), Vec3.ZERO, 0.0f));
        assertFalse(SpacialExcisionMath.isFinite(new Vec3(0.0, Double.POSITIVE_INFINITY, 0.0)));
    }

    @Test
    void transitionRampsInAndOutWithoutChangingTheSharedEndTick() {
        assertEquals(0.0f, SpacialExcisionMath.transitionProgress(100, 100, 700));
        assertEquals(0.5f, SpacialExcisionMath.transitionProgress(102, 100, 700), EPSILON);
        assertEquals(1.0f, SpacialExcisionMath.transitionProgress(200, 100, 700), EPSILON);
        assertEquals(0.5f, SpacialExcisionMath.transitionProgress(698, 100, 700), EPSILON);
        assertEquals(0.0f, SpacialExcisionMath.transitionProgress(700, 100, 700), EPSILON);
    }

    @Test
    void frontClipInterpolationKeepsTheVisiblePartOfCrossingGeometry() {
        assertEquals(0.525f,
                SpacialExcisionMath.frontClipInterpolation(-1.0f, 1.0f, 0.05f),
                EPSILON);
        assertEquals(0.475f,
                SpacialExcisionMath.frontClipInterpolation(1.0f, -1.0f, 0.05f),
                EPSILON);
        assertTrue(Float.isNaN(SpacialExcisionMath.frontClipInterpolation(
                1.0f, 1.0f, 0.05f)));
        assertTrue(Float.isNaN(SpacialExcisionMath.frontClipInterpolation(
                Float.NaN, 1.0f, 0.05f)));
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
