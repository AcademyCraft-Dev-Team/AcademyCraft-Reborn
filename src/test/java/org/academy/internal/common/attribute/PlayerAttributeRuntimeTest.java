package org.academy.internal.common.attribute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAttributeRuntimeTest {
    @Test
    void linearBonusesUseThePropsConversions() {
        assertEquals(0.0, PlayerAttributeRuntime.muscleDamageBonus(0.0));
        assertEquals(0.005, PlayerAttributeRuntime.muscleDamageBonus(1.0));
        assertEquals(0.02, PlayerAttributeRuntime.enduranceHealthBonus(1.0));
        assertEquals(0.001,
                PlayerAttributeRuntime.dexteritySpeedBonus(1.0), 1.0E-12);
        assertEquals(0.0007835052735414294,
                PlayerAttributeRuntime.dexterityJumpStrengthBonus(1.0), 1.0E-12);
        assertEquals(3.0,
                PlayerAttributeRuntime.dexteritySafeFallDistanceBonus(2_000.0));
        assertEquals(0, PlayerAttributeRuntime.logarithmicLevel(999.99));
        assertEquals(1, PlayerAttributeRuntime.logarithmicLevel(1_000.0));
    }

    @Test
    void invalidOrNegativeInputsCannotProduceNegativeBonuses() {
        assertEquals(0.0, PlayerAttributeRuntime.muscleDamageBonus(-10.0));
        assertEquals(0.0, PlayerAttributeRuntime.enduranceHealthBonus(Double.NaN));
        assertEquals(0.0, PlayerAttributeRuntime.dexteritySpeedBonus(Double.POSITIVE_INFINITY));
        assertEquals(0.0, PlayerAttributeRuntime.dexteritySafeFallDistanceBonus(Double.NaN));
        assertEquals(0, PlayerAttributeRuntime.logarithmicLevel(-1.0));
    }

    @Test
    void maxHealthChangesNeverHealOrResurrectThePlayer() {
        assertEquals(20.0f, PlayerAttributeRuntime.healthAfterMaxHealthChange(20.0f, 30.0f));
        assertEquals(15.0f, PlayerAttributeRuntime.healthAfterMaxHealthChange(15.0f, 30.0f));
        assertEquals(0.0f, PlayerAttributeRuntime.healthAfterMaxHealthChange(0.0f, 30.0f));
        assertEquals(10.0f, PlayerAttributeRuntime.healthAfterMaxHealthChange(15.0f, 10.0f));
    }
    @Test
    void allResistanceLevelsPreserveAuthoritativeClientHealthIncludingDeath() {
        for (var resistance : new double[]{0.0, 2.0, 6.0, 8.0}) {
            for (var requested : new float[]{18.0f, 3.0f, 0.0f}) {
                assertEquals(requested, PlayerAttributeRuntime.healthAfterResistanceWrite(
                        true, 20.0f, requested, resistance, 0.10));
                assertEquals(requested, PlayerAttributeRuntime.healthAfterResistanceWrite(
                        true, 20.0f, requested, resistance, 0.08));
            }
        }
    }

    @Test
    void serverResistanceStillReducesDamageWithoutPreventingOverkillDeath() {
        assertEquals(12.0f, PlayerAttributeRuntime.healthAfterResistanceWrite(
                false, 20.0f, 10.0f, 2.0, 0.10), 0.0001f);
        assertEquals(11.6f, PlayerAttributeRuntime.healthAfterResistanceWrite(
                false, 20.0f, 10.0f, 2.0, 0.08), 0.0001f);
        assertEquals(-28.0f, PlayerAttributeRuntime.healthAfterResistanceWrite(
                false, 20.0f, -100.0f, 6.0, 0.10), 0.0001f);
        assertEquals(20.0f, PlayerAttributeRuntime.healthAfterResistanceWrite(
                false, 5.0f, 20.0f, 8.0, 0.10));
    }
}
