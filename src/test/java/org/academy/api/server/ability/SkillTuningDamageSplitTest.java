package org.academy.api.server.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The percentage max-health part of a hit must never be scaled by the ordinary damage multiplier.
 */
class SkillTuningDamageSplitTest {
    @Test
    void ordinaryDamageTakesTheFullMultiplier() {
        assertEquals(30.0f, SkillTuning.applyDamageParts(10.0f, 0.0f, 3.0f, 0.0f));
    }

    @Test
    void percentagePartIgnoresTheOrdinaryMultiplier() {
        // 10 damage of which 4 is the percentage term: 6 * 3 + 4 (not scaled) = 22.
        assertEquals(22.0f, SkillTuning.applyDamageParts(10.0f, 4.0f, 3.0f, 4.0f));
    }

    @Test
    void aFullyPercentageHitIsNotScaledAtAll() {
        assertEquals(5.0f, SkillTuning.applyDamageParts(5.0f, 5.0f, 100.0f, 5.0f));
    }

    @Test
    void dedicatedPercentageMultiplierAppliesToThatTermOnly() {
        // ordinary 6 * 2 (skill) + percentage 4 * 1.5 (dedicated) = 12 + 6 = 18.
        assertEquals(18.0f, SkillTuning.applyDamageParts(10.0f, 4.0f, 2.0f, 6.0f));
    }

    @Test
    void declaredPercentagePartIsClampedToTheTotalDamage() {
        // A declaration larger than the hit cannot make the ordinary part negative.
        assertEquals(10.0f, SkillTuning.applyDamageParts(10.0f, 50.0f, 4.0f, 10.0f));
    }

    @Test
    void gateOffDropsOnlyThePercentageTerm() {
        // Percentage part zeroed by the gate; ordinary part still scaled: 6 * 3 = 18.
        assertEquals(18.0f, SkillTuning.applyDamageParts(10.0f, 4.0f, 3.0f, 0.0f));
    }

    @Test
    void invalidInputsStaySafe() {
        // A non-positive percentage part just means "no percentage term"; the ordinary part still scales.
        assertEquals(30.0f, SkillTuning.applyDamageParts(10.0f, -5.0f, 3.0f, -5.0f));
        // A non-finite ordinary multiplier falls back to neutral.
        assertEquals(10.0f, SkillTuning.applyDamageParts(10.0f, 0.0f, Float.NaN, 0.0f));
    }
}
