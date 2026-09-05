package org.academy.internal.common.ability.aeromanip;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AeromanipChargeProgressTest {
    @Test
    void localFullProgressWaitsForServerReadinessAtTheReleaseBoundary() {
        var progress = new AeromanipChargeProgress();
        progress.begin(1);
        progress.accept(1, AeromanipChargeTier.HALF.ordinal());
        assertTrue(progress.awaitingConfirmation(24));
        assertEquals(AeromanipChargeTier.HALF, progress.tier());
        progress.accept(1, AeromanipChargeTier.FULL.ordinal());
        assertFalse(progress.awaitingConfirmation(24));
        assertEquals(AeromanipChargeTier.FULL, progress.tier());
    }

    @Test
    void delayedAcknowledgementCannotCompleteANewerCharge() {
        var progress = new AeromanipChargeProgress();
        progress.begin(1);
        progress.begin(2);
        assertFalse(progress.accept(1, AeromanipChargeTier.FULL.ordinal()));
        assertTrue(progress.awaitingConfirmation(24));
        assertEquals(AeromanipChargeTier.INSTANT, progress.tier());
    }

    @Test
    void confirmedFullChargeDoesNotRegressWithDelayedHalfOrLowClientFrameRate() {
        var progress = new AeromanipChargeProgress();
        progress.begin(1);
        progress.accept(1, AeromanipChargeTier.FULL.ordinal());
        progress.accept(1, AeromanipChargeTier.HALF.ordinal());
        assertEquals(AeromanipChargeTier.FULL, progress.tier());
        assertFalse(progress.awaitingConfirmation(22));
    }
}
