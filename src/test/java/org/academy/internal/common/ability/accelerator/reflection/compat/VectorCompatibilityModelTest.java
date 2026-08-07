package org.academy.internal.common.ability.accelerator.reflection.compat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorCompatibilityModelTest {
    @Test
    void executionPolicyClampsUntrustedProfileLimits() {
        var policy = new VectorExecutionPolicy(
                true,
                true,
                true,
                null,
                null,
                Integer.MAX_VALUE,
                Double.POSITIVE_INFINITY
        );

        assertEquals(VectorExecutionPolicy.HARD_MAXIMUM_TARGETS, policy.maximumTargets());
        assertEquals(VectorExecutionPolicy.DEFAULT_MAXIMUM_RANGE, policy.maximumRange());
        assertEquals(VectorBlockPolicy.CLIP_NO_BREAK, policy.blockPolicy());
        assertEquals(VectorVisualStyle.ENERGY, policy.visualStyle());
    }

    @Test
    void executionPolicyNeverExceedsHardRangeLimit() {
        var policy = new VectorExecutionPolicy(
                false,
                false,
                false,
                VectorBlockPolicy.PASS_THROUGH,
                VectorVisualStyle.NONE,
                0,
                10_000.0
        );

        assertEquals(1, policy.maximumTargets());
        assertEquals(VectorExecutionPolicy.HARD_MAXIMUM_RANGE, policy.maximumRange());
    }

    @Test
    void projectileMarkerCarriesRedirectOwnershipAndDepth() {
        var original = UUID.randomUUID();
        var redirector = UUID.randomUUID();
        var data = new VectorProjectileRedirectData(
                1,
                original,
                redirector,
                VectorRedirectKind.REFRACTION,
                42L
        );

        assertTrue(data.isRedirected());
        assertEquals(1, data.redirectDepth());
        assertEquals(original, data.originalOwnerId());
        assertEquals(redirector, data.redirectorId());
        assertEquals(VectorRedirectKind.REFRACTION, data.kind());
        assertEquals(42L, data.fingerprint());
        assertFalse(VectorProjectileRedirectData.none().isRedirected());
    }

    @Test
    void confidenceOrderingIsExplicit() {
        assertTrue(VectorAttackConfidence.EXACT.atLeast(VectorAttackConfidence.HIGH));
        assertTrue(VectorAttackConfidence.MEDIUM.atLeast(VectorAttackConfidence.MEDIUM));
        assertFalse(VectorAttackConfidence.LOW.atLeast(VectorAttackConfidence.MEDIUM));
        assertFalse(VectorAttackConfidence.NONE.atLeast(VectorAttackConfidence.LOW));
    }
}
