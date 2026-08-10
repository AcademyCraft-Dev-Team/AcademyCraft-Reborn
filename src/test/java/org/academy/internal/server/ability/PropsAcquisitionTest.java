package org.academy.internal.server.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PropsAcquisitionTest {
    @Test
    void meleeDamageUsesActualHealthDamageAndCapsEachHitAtTen() {
        assertEquals(4.25, PropsAcquisition.meleeDamage(4.25));
        assertEquals(10.0, PropsAcquisition.meleeDamage(18.0));
        assertEquals(0.0, PropsAcquisition.meleeDamage(-1.0));
        assertEquals(0.0, PropsAcquisition.meleeDamage(Double.NaN));
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
    void perceptionOnlyUsesPositiveExperienceChanges() {
        assertEquals(12, PropsAcquisition.experienceGained(12));
        assertEquals(0, PropsAcquisition.experienceGained(0));
        assertEquals(0, PropsAcquisition.experienceGained(-7));
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
