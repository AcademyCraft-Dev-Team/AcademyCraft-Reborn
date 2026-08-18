package org.academy.internal.common.ability.mentalout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MentaloutControlCostTest {
    @Test
    void usesMaxHealthTiersAtTheirInclusiveBoundaries() {
        assertMultiplier(0.5f, 10.0f, 1.0f);
        assertMultiplier(1.0f, 40.0f, 1.0f);
        assertMultiplier(2.0f, 160.0f, 1.0f);
        assertMultiplier(4.0f, 640.0f, 1.0f);
        assertMultiplier(8.0f, 2_560.0f, 1.0f);
    }

    @Test
    void advancesToTheNextTierImmediatelyAboveABoundary() {
        assertMultiplier(1.0f, 10.01f, 1.0f);
        assertMultiplier(2.0f, 40.01f, 1.0f);
        assertMultiplier(4.0f, 160.01f, 1.0f);
        assertMultiplier(8.0f, 640.01f, 1.0f);
    }

    @Test
    void abilityStrengthRaisesEveryHealthThresholdByPercentage() {
        assertMultiplier(0.5f, 15.0f, 1.5f);
        assertMultiplier(1.0f, 60.0f, 1.5f);
        assertMultiplier(2.0f, 240.0f, 1.5f);
        assertMultiplier(0.5f, 20.0f, 2.0f);
        assertMultiplier(1.0f, 80.0f, 2.0f);
    }

    @Test
    void invalidStrengthFallsBackToNormalThresholds() {
        assertMultiplier(1.0f, 20.0f, 0.0f);
        assertMultiplier(1.0f, 20.0f, Float.NaN);
    }

    private static void assertMultiplier(float expected, float maxHealth, float abilityStrength) {
        assertEquals(expected, MentaloutControlCost.multiplierFor(maxHealth, abilityStrength), 0.0001f);
    }
}
