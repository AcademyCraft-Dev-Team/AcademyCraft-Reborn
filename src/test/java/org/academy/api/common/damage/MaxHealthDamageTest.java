package org.academy.api.common.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaxHealthDamageTest {
    @Test
    void fivePercentAndLowerStayUnchangedIncludingLargeOriginalBase() {
        for (var ratio : new float[]{0.0f, 0.01f, 0.02f, 0.05f}) {
            var damage = MaxHealthDamage.rebalance(1000.0f, ratio);
            assertEquals(1000.0f, damage.baseDamage());
            assertEquals(ratio, damage.maxHealthRatio());
            assertEquals(1000.0f + 100.0f * ratio, damage.calculate(100.0f));
        }
    }

    @Test
    void removedPercentagePointsBecomeFiveBaseDamageEach() {
        var damage = MaxHealthDamage.rebalance(10.0f, 0.30f);
        assertEquals(135.0f, damage.baseDamage(), 0.0001f);
        assertEquals(0.05f, damage.maxHealthRatio());
        assertEquals(140.0f, damage.calculate(100.0f), 0.0001f);
        assertEquals(12.5f, MaxHealthDamage.rebalance(10.0f, 0.055f).baseDamage(), 0.0001f);
    }

    @Test
    void capAppliesOnlyToAddedDamageBeforeExistingMultipliers() {
        assertEquals(195.0f, MaxHealthDamage.rebalance(0.0f, 0.44f).baseDamage(), 0.0001f);
        assertEquals(200.0f, MaxHealthDamage.rebalance(0.0f, 0.45f).baseDamage(), 0.0001f);
        var damage = MaxHealthDamage.rebalance(1000.0f, 2.0f);
        assertEquals(1200.0f, damage.baseDamage());
        assertEquals(0.05f, damage.maxHealthRatio());
        assertEquals(1700.0f, damage.calculate(10000.0f));
        assertEquals(2900.0f, damage.calculate(10000.0f, 2.0f));
        assertEquals(500.0f, damage.calculate(10000.0f, 0.0f));
    }

    @Test
    void rejectsNonFiniteProfilesAndClampsNegativeInputs() {
        assertThrows(IllegalArgumentException.class, () -> MaxHealthDamage.rebalance(Float.NaN, 0.2f));
        assertThrows(IllegalArgumentException.class, () -> MaxHealthDamage.rebalance(0.0f, Float.POSITIVE_INFINITY));
        assertEquals(0.0f, MaxHealthDamage.rebalance(-1.0f, -1.0f).calculate(-1.0f));
        assertEquals(0.0f, MaxHealthDamage.rebalance(0.0f, 0.2f).calculate(Float.NaN));
    }
}
