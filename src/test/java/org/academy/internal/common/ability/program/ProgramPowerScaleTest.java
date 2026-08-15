package org.academy.internal.common.ability.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgramPowerScaleTest {
    @Test
    void scalesDamageLinearlyAndCostQuadratically() {
        assertEquals(0.0f, ProgramPowerScale.damageMultiplier(0.0f));
        assertEquals(1.0f, ProgramPowerScale.damageMultiplier(1.0f));
        assertEquals(2.0f, ProgramPowerScale.damageMultiplier(2.0f));
        assertEquals(0.0f, ProgramPowerScale.cost(20.0f, 0.0f));
        assertEquals(20.0f, ProgramPowerScale.cost(20.0f, 1.0f));
        assertEquals(80.0f, ProgramPowerScale.cost(20.0f, 2.0f));
    }

    @Test
    void interpolatesFormerTiersContinuouslyAndRejectsOutOfRangePower() {
        assertEquals(6.0, ProgramPowerScale.interpolate(0.0f, 6.0, 12.0, 20.0));
        assertEquals(9.0, ProgramPowerScale.interpolate(0.5f, 6.0, 12.0, 20.0));
        assertEquals(16.0, ProgramPowerScale.interpolate(1.5f, 6.0, 12.0, 20.0));
        assertThrows(IllegalArgumentException.class,
                () -> ProgramPowerScale.damageMultiplier(2.01f));
    }
}
