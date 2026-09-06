package org.academy.internal.server.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropsAcquisitionTest {
    @Test
    void damageRewardsAreProportionalWithoutTheOldPerHitCap() {
        assertEquals(0.85, PropsAcquisition.damageReward(4.25), 1.0E-12);
        assertEquals(3.6, PropsAcquisition.damageReward(18.0), 1.0E-12);
        assertEquals(0.0, PropsAcquisition.damageReward(-1.0));
        assertEquals(0.0, PropsAcquisition.damageReward(Double.NaN));
    }

    @Test
    void healthLossCannotExceedHealthBeforeTheHit() {
        assertEquals(3.5, PropsAcquisition.healthLost(12.0, 3.5));
        assertEquals(2.0, PropsAcquisition.healthLost(2.0, 30.0));
        assertEquals(0.0, PropsAcquisition.healthLost(12.0, -1.0));
    }

    @Test
    void foodRewardOnlyUsesRestoredFoodLevel() {
        assertEquals(4, PropsAcquisition.foodRestored(11, 15));
        assertEquals(1, PropsAcquisition.foodRestored(19, 20));
        assertEquals(0, PropsAcquisition.foodRestored(20, 20));
        assertEquals(0, PropsAcquisition.foodRestored(15, 14));
    }

    @Test
    void jumpsAreRateLimitedAndDoNotAccumulateSuppressedRewards() {
        assertTrue(PropsAcquisition.canRewardJump(1, 0, Long.MIN_VALUE));
        assertFalse(PropsAcquisition.canRewardJump(0, 100, Long.MIN_VALUE));
        for (var tick = 0; tick < 4; tick++) {
            assertFalse(PropsAcquisition.canRewardJump(10, tick, 0));
        }
        assertTrue(PropsAcquisition.canRewardJump(10, 4, 0));
        assertFalse(PropsAcquisition.canRewardJump(0, 5, 0));
    }

    @Test
    void movementRewardsWholeBlocksAndCarriesPartialCentimeters() {
        var progress = PropsAcquisition.distanceProgress(75, 150, 100);
        assertEquals(1, progress.blocks());
        assertEquals(25, progress.remainingCentimeters());

        var resetStat = PropsAcquisition.distanceProgress(25, 10, 200);
        assertEquals(0, resetStat.blocks());
        assertEquals(25, resetStat.remainingCentimeters());
    }

    @Test
    void jumpsUseEveryPositiveStatIncrease() {
        assertEquals(3, PropsAcquisition.statIncrease(13, 10));
        assertEquals(0, PropsAcquisition.statIncrease(4, 10));
    }
}
