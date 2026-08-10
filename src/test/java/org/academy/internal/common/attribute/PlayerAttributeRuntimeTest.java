package org.academy.internal.common.attribute;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAttributeRuntimeTest {
    @Test
    void linearBonusesUseThePropsConversions() {
        assertEquals(0.0, PlayerAttributeRuntime.muscleDamageBonus(0.0));
        assertEquals(0.05, PlayerAttributeRuntime.muscleDamageBonus(1.0));
        assertEquals(0.1, PlayerAttributeRuntime.enduranceHealthBonus(1.0));
        assertEquals(0.002,
                PlayerAttributeRuntime.dexteritySpeedBonus(1.0), 1.0E-12);
        assertEquals(Mth.sqrt(1.005f) - 1.0,
                PlayerAttributeRuntime.dexterityJumpStrengthBonus(1.0), 1.0E-12);
        assertEquals(0, PlayerAttributeRuntime.logarithmicLevel(199.99));
        assertEquals(1, PlayerAttributeRuntime.logarithmicLevel(200.0));
    }

    @Test
    void invalidOrNegativeInputsCannotProduceNegativeBonuses() {
        assertEquals(0.0, PlayerAttributeRuntime.muscleDamageBonus(-10.0));
        assertEquals(0.0, PlayerAttributeRuntime.enduranceHealthBonus(Double.NaN));
        assertEquals(0.0, PlayerAttributeRuntime.dexteritySpeedBonus(Double.POSITIVE_INFINITY));
        assertEquals(0, PlayerAttributeRuntime.logarithmicLevel(-1.0));
    }

    @Test
    void maxHealthChangesNeverHealOrResurrectThePlayer() {
        assertEquals(20.0f, PlayerAttributeRuntime.healthAfterMaxHealthChange(20.0f, 30.0f));
        assertEquals(15.0f, PlayerAttributeRuntime.healthAfterMaxHealthChange(15.0f, 30.0f));
        assertEquals(0.0f, PlayerAttributeRuntime.healthAfterMaxHealthChange(0.0f, 30.0f));
        assertEquals(10.0f, PlayerAttributeRuntime.healthAfterMaxHealthChange(15.0f, 10.0f));
    }
}
