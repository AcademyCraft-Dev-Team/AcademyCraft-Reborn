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
        assertEquals(9.0, PropsMath.muscleDamageBonus(2_000.0));
        assertEquals(40.0, PropsMath.enduranceHealthBonus(2_000.0));
        assertEquals(2.0, PropsMath.dexteritySpeedBonus(2_000.0));
        assertEquals(3.0, PropsMath.dexterityJumpHeightBonus(2_000.0));
        assertEquals(3.0, PropsMath.dexteritySafeFallDistanceBonus(2_000.0));
        assertEquals(2.0, PropsMath.perceptionEnchantmentBonus(2_000.0));
        assertEquals(1.0, PropsMath.perceptionExperienceMultiplier(2_000.0));
        assertEquals(1.2, PropsMath.neuralIterationMultiplier(2_000.0));
    }

    @Test
    void stepHeightBonusesStackAndDisappearBelowTheirThresholds() {
        for (var value : new double[]{0.0, 799.99, Double.NaN, -1.0}) {
            assertEquals(0.0, PropsMath.dexterityStepHeightBonus(value));
        }
        for (var value : new double[]{800.0, 1_199.99}) {
            assertEquals(1.0, PropsMath.dexterityStepHeightBonus(value));
        }
        for (var value : new double[]{1_200.0, 2_000.0}) {
            assertEquals(1.3, PropsMath.dexterityStepHeightBonus(value));
        }
    }

    @Test
    void muscleDamageCapsTheLinearPartAndStacksAllThreeMilestones() {
        var cases = new double[][]{
                {0.0, 0.0}, {100.0, 0.5}, {799.99, 3.99995},
                {800.0, 5.0}, {1_199.99, 6.99995}, {1_200.0, 8.0},
                {1_599.99, 8.0}, {1_600.0, 9.0}, {2_000.0, 9.0},
                {1_000_000_000.0, 9.0}, {-1.0, 0.0}, {Double.NaN, 0.0},
                {Double.POSITIVE_INFINITY, 0.0}
        };
        for (var sample : cases) {
            assertEquals(sample[1], PropsMath.muscleDamageBonus(sample[0]), 1.0E-10);
        }
    }

    @Test
    void fractionalEnchantmentLevelsUseTheRemainderAsAProbability() {
        assertEquals(0.1, PropsMath.perceptionEnchantmentBonus(100.0));
        assertEquals(1, PropsMath.rollPerceptionEnchantmentBonus(100.0, 0.099));
        assertEquals(0, PropsMath.rollPerceptionEnchantmentBonus(100.0, 0.1));
        assertEquals(2, PropsMath.rollPerceptionEnchantmentBonus(1_200.0, 0.199));
        assertEquals(1, PropsMath.rollPerceptionEnchantmentBonus(1_200.0, 0.201));
        assertEquals(1, PropsMath.rollPerceptionEnchantmentBonus(1_000.0, 0.0));
        assertEquals(0, PropsMath.rollPerceptionEnchantmentBonus(Double.NaN, 0.0));
        var sum = 0;
        for (var roll = 0; roll < 1_000; roll++) {
            sum += PropsMath.rollPerceptionEnchantmentBonus(100.0, (roll + 0.5) / 1_000.0);
        }
        assertEquals(100, sum);
    }

    @Test
    void jumpHeightMatchesTheRequestedRatioUnderDiscreteGravityAndDrag() {
        var baseHeight = PropsMath.jumpApexHeight(0.42);
        for (var value : new double[]{0.0, 1.0, 100.0, 500.0, 800.0, 1_200.0, 2_000.0}) {
            var velocity = 0.42 * (1.0 + PropsMath.dexterityJumpStrengthBonus(value));
            assertEquals(baseHeight * (1.0 + value * 0.0015),
                    PropsMath.jumpApexHeight(velocity), 1.0E-10);
        }
    }

    @Test
    void dexteritySafeFallCoversItsOwnJumpApex() {
        assertEquals(0.0, PropsMath.dexteritySafeFallDistanceBonus(0.0));
        assertEquals(0.0, PropsMath.dexteritySafeFallDistanceBonus(500.0));
        assertEquals(1.0, PropsMath.dexteritySafeFallDistanceBonus(1_000.0));
        assertEquals(0.0, PropsMath.dexteritySafeFallDistanceBonus(Double.NaN));

        var maximumJumpStrength = 0.42
                * (1.0 + PropsMath.dexterityJumpStrengthBonus(2_000.0));
        var protectedDistance = 3.0 + PropsMath.dexteritySafeFallDistanceBonus(2_000.0);
        assertEquals(true, protectedDistance >= PropsMath.jumpApexHeight(maximumJumpStrength));
    }
}
