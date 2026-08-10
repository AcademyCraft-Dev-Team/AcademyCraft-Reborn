package org.academy.internal.common.ability.meltdowner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeltdownerBeamDamageTest {
    @Test
    void damageIncludesBaseTargetHealthAndPlayerScaling() {
        assertEquals(21.0f, MeltdownerBeamDamage.calculate(20.0f, 0.01f, 100.0f, 1.0f, false));
        assertEquals(42.0f, MeltdownerBeamDamage.calculate(20.0f, 0.01f, 100.0f, 2.0f, false));
        assertEquals(31.0f, MeltdownerBeamDamage.calculate(20.0f, 0.01f, 100.0f, 1.0f, true));
    }

    @Test
    void damageRejectsNonFiniteValuesAndClampsNegativeInputs() {
        assertEquals(0.0f, MeltdownerBeamDamage.calculate(Float.NaN, 0.01f, 100.0f, 1.0f, false));
        assertEquals(0.0f, MeltdownerBeamDamage.calculate(20.0f, 0.01f, 100.0f, Float.POSITIVE_INFINITY, false));
        assertEquals(0.0f, MeltdownerBeamDamage.calculate(-20.0f, -0.01f, -100.0f, -1.0f, true));
    }
}
