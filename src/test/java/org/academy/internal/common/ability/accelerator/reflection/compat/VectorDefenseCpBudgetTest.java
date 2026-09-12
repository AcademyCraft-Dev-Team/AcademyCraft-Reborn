package org.academy.internal.common.ability.accelerator.reflection.compat;

import org.academy.internal.common.ability.accelerator.reflection.VectorDefenseProficiency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorDefenseCpBudgetTest {
    @Test
    void projectileChargesStopAtFortyPercentMaximumCp() {
        assertEquals(20.0f,
                VectorDefenseCpBudget.limitBaseCost(20.0f, 1.0f, 500.0f, 0.0f),
                1.0E-6f);
        assertEquals(10.0f,
                VectorDefenseCpBudget.limitBaseCost(20.0f, 1.0f, 500.0f, 190.0f),
                1.0E-6f);
        assertEquals(0.0f,
                VectorDefenseCpBudget.limitBaseCost(20.0f, 1.0f, 500.0f, 200.0f),
                1.0E-6f);
    }

    @Test
    void limitIsAppliedToActualCostAfterCalculationIntensity() {
        assertEquals(5.0f,
                VectorDefenseCpBudget.limitBaseCost(20.0f, 2.0f, 500.0f, 190.0f),
                1.0E-6f);
    }

    @Test
    void damageChargesUseOnlyTheRemainingFortyPercentWindowBudget() {
        assertEquals(200.0f,
                VectorDefenseCpBudget.limitBaseCost(250.0f, 1.0f, 500.0f, 0.0f),
                1.0E-6f);
        assertEquals(10.0f,
                VectorDefenseCpBudget.limitBaseCost(100.0f, 1.0f, 500.0f, 190.0f),
                1.0E-6f);
        assertEquals(0.0f,
                VectorDefenseCpBudget.limitBaseCost(100.0f, 1.0f, 500.0f, 200.0f),
                1.0E-6f);
    }

    @Test
    void damageWindowCapsCostWithoutReducingTheReflectedDamage() {
        var result = VectorDefenseProficiency.calculate(
                100.0f, 500.0f, 500.0f, 1.0f, 1, 0.0f, false);
        var budgetedCost = VectorDefenseCpBudget.limitBaseCost(
                result.baseCpCost(), 1.0f, 500.0f, 190.0f);

        assertEquals(100.0f, result.processedDamage(), 1.0E-6f);
        assertEquals(0.0f, result.remainingDamage(), 1.0E-6f);
        assertEquals(100.0f, result.baseCpCost(), 1.0E-6f);
        assertEquals(10.0f, budgetedCost, 1.0E-6f);
    }

    @Test
    void rollingWindowKeepsOnlyChargesFromTheLatestTwentyTicks() {
        var window = new VectorDefenseCpBudget.Window();
        window.record(100L, 120.0f);
        window.record(105L, 80.0f);

        assertEquals(200.0f, window.spentSince(119L, 100L), 1.0E-6f);
        assertEquals(80.0f, window.spentSince(120L, 101L), 1.0E-6f);
        window.record(120L, 30.0f);
        assertEquals(110.0f, window.spentSince(120L, 101L), 1.0E-6f);
    }

    @Test
    void invalidBudgetInputsCannotCreateFreeInterception() {
        assertTrue(Float.isNaN(VectorDefenseCpBudget
                .limitBaseCost(20.0f, Float.NaN, 500.0f, 0.0f)));
        assertTrue(Float.isNaN(VectorDefenseCpBudget
                .limitBaseCost(20.0f, 1.0f, -1.0f, 0.0f)));
    }
}
