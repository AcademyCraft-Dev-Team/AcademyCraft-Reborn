package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void inferredAndDamageFallbackTiersDoNotRenderReturnBeams() {
        assertTrue(VectorCompatibilityTier.INFERRED_HITSCAN.usesFallbackVisuals());
        assertTrue(VectorCompatibilityTier.DAMAGE_FALLBACK.usesFallbackVisuals());
        assertFalse(VectorCompatibilityTier.PROFILED_LINEAR.usesFallbackVisuals());
        assertFalse(VectorCompatibilityTier.NATIVE_EXACT.usesFallbackVisuals());
    }

    @Test
    void onlyExplicitLinearProfilesMayRenderSyntheticReturnBeams() {
        assertTrue(VectorCompatibilityTier.PROFILED_LINEAR.permitsSyntheticReturnVisual());
        assertFalse(VectorCompatibilityTier.NATIVE_EXACT.permitsSyntheticReturnVisual());
        assertFalse(VectorCompatibilityTier.STANDARD_PROJECTILE.permitsSyntheticReturnVisual());
        assertFalse(VectorCompatibilityTier.INFERRED_HITSCAN.permitsSyntheticReturnVisual());
        assertFalse(VectorCompatibilityTier.DAMAGE_FALLBACK.permitsSyntheticReturnVisual());
        assertFalse(VectorCompatibilityTier.PASS_THROUGH.permitsSyntheticReturnVisual());
        assertEquals(VectorVisualStyle.NONE, VectorExecutionPolicy.safeDefault().visualStyle());
    }

    @Test
    void refractionAbsorbsWithoutSubmittingRedirectedEntityDamage() {
        assertTrue(VectorRedirectKind.REFLECTION.dealsRedirectedEntityDamage());
        assertFalse(VectorRedirectKind.REFRACTION.dealsRedirectedEntityDamage());
    }

    @Test
    void vanillaAttackerDamageTypesAreNotRejectedAsMeleeByName() {
        assertFalse(VectorExternalAttackClassifier.isExplicitlyDeniedDamageName("player"));
        assertFalse(VectorExternalAttackClassifier.isExplicitlyDeniedDamageName("player_attack"));
        assertFalse(VectorExternalAttackClassifier.isExplicitlyDeniedDamageName("mob"));
        assertFalse(VectorExternalAttackClassifier.isExplicitlyDeniedDamageName("mob_attack"));
        assertTrue(VectorExternalAttackClassifier.isExplicitlyDeniedDamageName("fall"));
        assertTrue(VectorExternalAttackClassifier.isExplicitlyDeniedDamageName("magic"));
    }

    @Test
    void anomalousDamageThresholdIsStrictAndFinite() {
        assertFalse(VectorIncomingDamageCoordinator.isAnomalousDamage(100_000.0f));
        assertTrue(VectorIncomingDamageCoordinator.isAnomalousDamage(100_000.01f));
        assertTrue(VectorIncomingDamageCoordinator.isAnomalousDamage(Float.MAX_VALUE));
        assertFalse(VectorIncomingDamageCoordinator.isAnomalousDamage(Float.POSITIVE_INFINITY));
        assertFalse(VectorIncomingDamageCoordinator.isAnomalousDamage(Float.NaN));
    }

    @Test
    void recentAttributionEvidenceExpiresAndCannotCrossDimensionsOrDamageTypes() {
        assertTrue(VectorAttackAttributionResolver.isRecentEvidence(110L, 100L, true, true));
        assertFalse(VectorAttackAttributionResolver.isRecentEvidence(111L, 100L, true, true));
        assertFalse(VectorAttackAttributionResolver.isRecentEvidence(99L, 100L, true, true));
        assertFalse(VectorAttackAttributionResolver.isRecentEvidence(105L, 100L, false, true));
        assertFalse(VectorAttackAttributionResolver.isRecentEvidence(105L, 100L, true, false));
    }

    @Test
    void compatibilityEffectsAndAccumulatedDamageUseTheirExpectedWindows() {
        assertFalse(VectorCompatibilityEffectLimiter.shouldEmit(103L, 100L));
        assertTrue(VectorCompatibilityEffectLimiter.shouldEmit(104L, 100L));
        assertFalse(VectorReflectedDamageAccumulator.shouldFlush(109L, 110L));
        assertTrue(VectorReflectedDamageAccumulator.shouldFlush(110L, 110L));
    }

    @Test
    void continuousEnvironmentalFeedbackUsesIndependentVisualAndSoundIntervals() {
        var visualEmissions = 0;
        var soundEmissions = 0;
        var lastVisual = Long.MIN_VALUE;
        var lastSound = Long.MIN_VALUE;

        for (var tick = 0L; tick < 100L; tick++) {
            if (VectorEnvironmentalFeedbackController.shouldEmit(
                    tick,
                    lastVisual,
                    VectorEnvironmentalFeedbackController.VISUAL_INTERVAL_TICKS
            )) {
                visualEmissions++;
                lastVisual = tick;
            }
            if (VectorEnvironmentalFeedbackController.shouldEmit(
                    tick,
                    lastSound,
                    VectorEnvironmentalFeedbackController.SOUND_INTERVAL_TICKS
            )) {
                soundEmissions++;
                lastSound = tick;
            }
        }

        assertEquals(5, visualEmissions);
        assertEquals(3, soundEmissions);
        assertFalse(VectorEnvironmentalFeedbackController.isExpired(17L, 5L));
        assertTrue(VectorEnvironmentalFeedbackController.isExpired(18L, 5L));
    }

    @Test
    void onlyPositionalContinuousEnvironmentalDamageTypesAreLimited() {
        assertTrue(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:lava"));
        assertTrue(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:cactus"));
        assertTrue(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:hot_floor"));
        assertTrue(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:in_fire"));
        assertTrue(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:campfire"));
        assertTrue(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:fall"));
        assertTrue(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:sweet_berry_bush"));
        assertFalse(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:on_fire"));
        assertFalse(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:drown"));
        assertFalse(VectorEnvironmentalFeedbackController.isSupportedDamageType("minecraft:starve"));
    }

    @Test
    void environmentalOriginFacesTheHazardInsteadOfThePlayerView() {
        var defenderBounds = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
        var eastOrigin = VectorEnvironmentalFeedbackController.originFromSource(
                defenderBounds,
                new Vec3(1.0, 0.9, 0.0),
                false
        ).orElseThrow();
        var floorOrigin = VectorEnvironmentalFeedbackController.originFromSource(
                defenderBounds,
                new Vec3(0.0, -0.5, 0.0),
                true
        ).orElseThrow();
        var overlappingFloorOrigin = VectorEnvironmentalFeedbackController.originFromSource(
                defenderBounds,
                defenderBounds.getCenter(),
                true
        ).orElseThrow();

        assertEquals(1.0, eastOrigin.normal().x, 1.0E-8);
        assertEquals(0.35, eastOrigin.ringPosition().x, 1.0E-8);
        assertEquals(-1.0, floorOrigin.normal().y, 1.0E-8);
        assertEquals(-0.05, floorOrigin.ringPosition().y, 1.0E-8);
        assertEquals(-1.0, overlappingFloorOrigin.normal().y, 1.0E-8);
        assertTrue(VectorEnvironmentalFeedbackController.originFromSource(
                defenderBounds,
                defenderBounds.getCenter(),
                false
        ).isEmpty());
    }

    @Test
    void incomingDamageResultKeepsPartialDamageDistinctFromPassThrough() {
        var passThrough = VectorIncomingDamageResult.passThrough(12.0f);
        var partial = VectorIncomingDamageResult.partial(7.5f);
        var full = VectorIncomingDamageResult.fullRedirect();

        assertFalse(passThrough.handled());
        assertEquals(VectorIncomingDamageResult.Status.PARTIAL_REFLECTION, partial.status());
        assertEquals(7.5f, partial.remainingDamage());
        assertTrue(partial.handled());
        assertEquals(VectorIncomingDamageResult.Status.FULL_REDIRECT, full.status());
        assertEquals(0.0f, full.remainingDamage());
    }
}
