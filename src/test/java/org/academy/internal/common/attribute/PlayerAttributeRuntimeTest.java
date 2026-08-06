package org.academy.internal.common.attribute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAttributeRuntimeTest {
    @Test
    void logarithmicBonusesUseTheRequestedRounding() {
        assertEquals(0.0, PlayerAttributeRuntime.muscleDamageBonus(0.0));
        assertEquals(1.0, PlayerAttributeRuntime.muscleDamageBonus(1.0));
        assertEquals(2.0, PlayerAttributeRuntime.enduranceHealthBonus(1.0));
        assertEquals(Math.log(2.0) * 0.2,
                PlayerAttributeRuntime.dexteritySpeedBonus(1.0), 1.0E-12);
        assertEquals(Math.log(2.0),
                PlayerAttributeRuntime.enduranceJumpBonus(1.0), 1.0E-12);
        assertEquals(0, PlayerAttributeRuntime.logarithmicLevel(Math.exp(2.0) - 1.01));
        assertEquals(1, PlayerAttributeRuntime.logarithmicLevel(Math.exp(2.0) - 1.0));
    }

    @Test
    void invalidOrNegativeInputsCannotProduceNegativeBonuses() {
        assertEquals(0.0, PlayerAttributeRuntime.muscleDamageBonus(-10.0));
        assertEquals(0.0, PlayerAttributeRuntime.enduranceHealthBonus(Double.NaN));
        assertEquals(0.0, PlayerAttributeRuntime.dexteritySpeedBonus(Double.POSITIVE_INFINITY));
        assertEquals(0, PlayerAttributeRuntime.logarithmicLevel(-1.0));
    }
}
