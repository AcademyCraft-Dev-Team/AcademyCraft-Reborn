package org.academy.internal.common.ability.accelerator.reflection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorDefenseProficiencyTest {
    @Test
    void tenDamageUsesTheFourDiscreteCostTiers() {
        assertCost(0, 20.0f);
        assertCost(1, 10.0f);
        assertCost(2, 5.0f);
        assertCost(3, 5.0f);
    }

    @Test
    void insufficientCpOnlyProcessesAffordableDamage() {
        var result = VectorDefenseProficiency.calculate(10.0f, 9.0f, 1.5f, 0, false);

        assertEquals(3.0f, result.processedDamage(), 1.0E-6f);
        assertEquals(7.0f, result.remainingDamage(), 1.0E-6f);
        assertEquals(6.0f, result.baseCpCost(), 1.0E-6f);
    }

    @Test
    void debugModeProcessesFiniteDamageWithoutCost() {
        var result = VectorDefenseProficiency.calculate(10.0f, 0.0f, 3.0f, 0, true);

        assertTrue(result.isFull());
        assertEquals(10.0f, result.processedDamage(), 1.0E-6f);
        assertEquals(0.0f, result.baseCpCost(), 1.0E-6f);
    }

    @Test
    void invalidNumbersNeverProduceFreeProtection() {
        assertEquals(0.0f, VectorDefenseProficiency
                .calculate(Float.POSITIVE_INFINITY, 100.0f, 1.0f, 3, false)
                .processedDamage());
        assertEquals(0.0f, VectorDefenseProficiency
                .calculate(10.0f, Float.NaN, 1.0f, 3, false)
                .processedDamage());
        assertEquals(0.0f, VectorDefenseProficiency
                .calculate(10.0f, 100.0f, 0.0f, 3, false)
                .processedDamage());
    }

    private static void assertCost(int milestone, float expectedCost) {
        var result = VectorDefenseProficiency.calculate(10.0f, 100.0f, 1.0f, milestone, false);
        assertTrue(result.isFull());
        assertEquals(expectedCost, result.baseCpCost(), 1.0E-6f);
    }
}
