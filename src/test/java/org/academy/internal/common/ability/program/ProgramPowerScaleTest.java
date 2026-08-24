package org.academy.internal.common.ability.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramPowerScaleTest {
    @Test
    void usesMarginalDamageAndCostCurvesWithExactEndpoints() {
        assertEquals(0.01f, ProgramPowerScale.damageMultiplier(0.0f));
        assertEquals(0.01f, ProgramPowerScale.damageMultiplier(0.01f));
        assertEquals(1.0f, ProgramPowerScale.damageMultiplier(1.0f));
        assertEquals(2.0f, ProgramPowerScale.damageMultiplier(2.0f));
        assertEquals(2.0f, ProgramPowerScale.cost(20.0f, 0.01f), 1.0E-6f);
        assertEquals(20.0f, ProgramPowerScale.cost(20.0f, 1.0f));
        assertEquals(80.0f, ProgramPowerScale.cost(20.0f, 2.0f));
    }

    @Test
    void changesDiminishNearTheMinimumAndMaximum() {
        var lowCostStep = ProgramPowerScale.costMultiplier(0.02f)
                - ProgramPowerScale.costMultiplier(0.01f);
        var middleCostStep = ProgramPowerScale.costMultiplier(0.51f)
                - ProgramPowerScale.costMultiplier(0.50f);
        var highCostStep = ProgramPowerScale.costMultiplier(2.0f)
                - ProgramPowerScale.costMultiplier(1.99f);
        var upperMiddleCostStep = ProgramPowerScale.costMultiplier(1.51f)
                - ProgramPowerScale.costMultiplier(1.50f);

        assertTrue(lowCostStep < middleCostStep);
        assertTrue(highCostStep < upperMiddleCostStep);
    }

    @Test
    void interpolatesFormerTiersContinuouslyAndRejectsOutOfRangePower() {
        assertEquals(6.0, ProgramPowerScale.interpolate(0.01f, 6.0, 12.0, 20.0));
        assertEquals(12.0, ProgramPowerScale.interpolate(1.0f, 6.0, 12.0, 20.0));
        assertEquals(20.0, ProgramPowerScale.interpolate(2.0f, 6.0, 12.0, 20.0));
        assertThrows(IllegalArgumentException.class,
                () -> ProgramPowerScale.damageMultiplier(2.01f));
    }
}
