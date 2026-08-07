package org.academy.internal.common.attribute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PropsMathTest {
    @Test
    void coefficientUsesExplicitHardCap() {
        assertEquals(1.0, PropsMath.acquisitionCoefficient(0.0));
        assertEquals(1.0 - 0.1075 * Math.log(101.0),
                PropsMath.acquisitionCoefficient(100.0), 1.0E-12);
        assertEquals(0.0, PropsMath.acquisitionCoefficient(2_000.0));
        assertEquals(0.0, PropsMath.acquisitionCoefficient(Double.POSITIVE_INFINITY));
    }

    @Test
    void awardsRespectCoefficientAndRemainingCapacity() {
        assertEquals(10.0, PropsMath.awardedAmount(0.0, 10.0, false));
        assertEquals(5.0, PropsMath.awardedAmount(1_995.0, 100.0, true));
        assertEquals(0.0, PropsMath.awardedAmount(2_000.0, 100.0, true));
        assertEquals(0.0, PropsMath.awardedAmount(0.0, Double.NaN, false));
    }

    @Test
    void requestedPassiveMultipliersAreExact() {
        assertEquals(100.0, PropsMath.muscleDamageBonus(2_000.0));
        assertEquals(200.0, PropsMath.enduranceHealthBonus(2_000.0));
        assertEquals(4.0, PropsMath.dexteritySpeedBonus(2_000.0));
        assertEquals(Math.sqrt(11.0) - 1.0, PropsMath.dexterityJumpStrengthBonus(2_000.0));
        assertEquals(10, PropsMath.perceptionEnchantmentBonus(2_000.0));
        assertEquals(1.1, PropsMath.perceptionExperienceMultiplier(2_000.0));
        assertEquals(1.2, PropsMath.neuralIterationMultiplier(2_000.0));
    }
}
