package org.academy.api.server.damage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthLossGuardsTest {
    @Test void partialBudgetPreservesUnpaidLoss() {
        var result = HealthLossGuards.resolve(20, 5, 4.5, 0.9);
        assertEquals(10, result.health(), 1e-8);
        assertEquals(5, result.absorbed(), 1e-8);
        assertEquals(4.5, result.cost(), 1e-8);
        assertFalse(result.fullyAbsorbed());
    }

    @Test void fullBudgetAbsorbsTheWholeLoss() {
        var result = HealthLossGuards.resolve(20, 5, 13.5, 0.9);
        assertEquals(20, result.health(), 1e-8);
        assertEquals(15, result.absorbed(), 1e-8);
        assertEquals(13.5, result.cost(), 1e-8);
        assertTrue(result.fullyAbsorbed());
    }
    @Test void lethalOverkillPaysOnlyForActualHealthLoss() {
        var result = HealthLossGuards.resolve(20, -200, 100, 0.9);
        assertEquals(20, result.health(), 1e-8);
        assertEquals(18, result.cost(), 1e-8);
        assertEquals(0, HealthLossGuards.resolve(20, -200, 0, 1).health());
    }
    @Test void healingAndRepeatedWriteAreFree() {
        assertEquals(0, HealthLossGuards.resolve(10, 20, 100, 1).cost());
        assertEquals(0, HealthLossGuards.resolve(10, 10, 100, 1).cost());
        assertEquals(0, HealthLossGuards.resolve(10, 5, 100, Double.NaN).cost());
    }
}
