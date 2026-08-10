package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedLinearAttackTest {
    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-6);
        assertEquals(expected.y, actual.y, 1.0E-6);
        assertEquals(expected.z, actual.z, 1.0E-6);
    }

    @Test
    void unreflectedDegenerateAttackDoesNotInventGeometry() {
        var point = new LinearSegment(new Vec3(2, 3, 4), new Vec3(2, 3, 4));
        var attack = ResolvedLinearAttack.unreflected(point);

        assertEquals(point, attack.original());
        assertEquals(point, attack.outbound());
        assertFalse(attack.isReflected());
        assertFalse(attack.isRedirected());
        assertFalse(attack.isReflection());
        assertFalse(attack.isRefraction());
        assertTrue(attack.returnSegment().isEmpty());
        assertTrue(attack.redirectedSegment().isEmpty());
        assertTrue(attack.reflectionCandidate().isEmpty());
        assertEquals(1.0, attack.reflectionProgress(), 1.0E-9);
        assertEquals(point.end(), attack.mirrorPoint());
        assertEquals(List.of(point), attack.segments());
    }

    @Test
    void blockedReturnKeepsItsEpsilonOriginAndReportsMirrorToEndpointLength() {
        var mirrorPoint = new Vec3(4, 0, 0);
        var returnSegment = new LinearSegment(
                new Vec3(4 - LinearReflectionResolver.RETURN_EPSILON, 0, 0),
                new Vec3(-6, 0, 0)
        );

        var limited = ResolvedLinearAttack.limitReturnSegment(returnSegment, 3.0);

        assertEquals(returnSegment.start(), limited.start());
        assertVecEquals(new Vec3(1 - LinearReflectionResolver.RETURN_EPSILON, 0, 0), limited.end());
        assertEquals(
                3.0 + LinearReflectionResolver.RETURN_EPSILON,
                ResolvedLinearAttack.calculateReturnVisualLength(mirrorPoint, limited),
                1.0E-6
        );
        assertEquals(0.0, ResolvedLinearAttack.calculateReturnVisualLength(mirrorPoint, null), 1.0E-9);
    }
}
