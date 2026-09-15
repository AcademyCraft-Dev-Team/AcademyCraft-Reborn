package org.academy.api.common.ability.electromaster;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElectromasterReworkTuningTest {
    @Test void progressionKeepsMassAndCpIndependent() {
        assertEquals(100, IronSandTuning.capacity(1, 0));
        assertEquals(180, IronSandTuning.capacity(1.5, 2), 1e-8);
        assertEquals(4.5, IronSandTuning.massCost(5, 1), 1e-8);
        assertEquals(9, IronSandTuning.massCost(10, 3), 1e-8);
        assertEquals(1, IronSandTuning.recoveryPerTick(2, false));
        assertEquals(2, IronSandTuning.recoveryPerTick(2, true));
        assertEquals(15, IronSandTuning.whipRange(2));
        assertEquals(20, IronSandTuning.cloudRadius(2));
    }

    @Test void chargeBonusIsBaseDamageAndKeepsExplicitAbilityPower() {
        assertEquals(10, IronSandTuning.whipBaseDamage(2, true));
        assertEquals(10, IronSandTuning.whipBaseDamage(3, false));
        assertEquals(45, IronSandTuning.scaleDamage(IronSandTuning.whipBaseDamage(3, true), 1.5, 2));
        assertEquals(48, IronSandTuning.scaleDamage(16, 1.5, 2));
        assertEquals(0, IronSandTuning.scaleDamage(Double.NaN, 1, 1));
        assertEquals(0, IronSandTuning.capacity(Double.POSITIVE_INFINITY, 2));
    }

    @Test void levitationAndTargetPullUseDifferentRanges() {
        assertArrayEquals(new double[]{4, 6, 8, 16}, new double[]{MagneticFieldTuning.supportRadius(0),
                MagneticFieldTuning.supportRadius(1), MagneticFieldTuning.supportRadius(2), MagneticFieldTuning.supportRadius(3)});
        assertEquals(48, MagneticFieldTuning.targetPullRange(1));
        assertEquals(60, MagneticFieldTuning.targetPullRange(2));
        assertEquals(2.645, MagneticFieldTuning.targetPullSpeed(2), 1e-9);
    }

    @Test void levitationDefaultsKeepRestHeightAboveTheSafetyFloor() {
        var tuning = LevitationTuning.DEFAULT;
        assertTrue(tuning.restClearance() >= MagneticMovement.MIN_CLEARANCE);
        assertTrue(tuning.maxClimbSpeed() > tuning.maxDescentSpeed(),
                "rising must stay more responsive than falling");
        assertEquals(1.5, tuning.restClearance(), 1e-9);
        assertEquals(6, tuning.graceTicks());
        assertEquals(1152, tuning.maxSupportSamples());
    }

    @Test void levitationTuningRejectsOutOfRangeAndNonFiniteEntries() {
        var fallback = LevitationTuning.DEFAULT;
        var broken = new LevitationTuning(
                Double.NaN, Double.NEGATIVE_INFINITY, Double.NaN, -1.0, Double.NaN, -0.5,
                Double.NaN, 4.0, 1, -9);
        assertEquals(fallback.restClearance(), broken.restClearance(), 1e-9);
        assertEquals(fallback.inputClearanceOffset(), broken.inputClearanceOffset(), 1e-9);
        assertEquals(fallback.followGain(), broken.followGain(), 1e-9);
        assertEquals(fallback.maxClimbSpeed(), broken.maxClimbSpeed(), 1e-9);
        assertEquals(fallback.maxDescentSpeed(), broken.maxDescentSpeed(), 1e-9);
        assertEquals(fallback.inputVerticalSpeed(), broken.inputVerticalSpeed(), 1e-9);
        assertEquals(fallback.sinkSpeed(), broken.sinkSpeed(), 1e-9);
        assertEquals(1.0, broken.graceSpeedFactor(), 1e-9);
        assertEquals(16, broken.maxSupportSamples());
        assertEquals(0, broken.graceTicks());
    }

    /**
     * A zero gain or speed would leave the mover with no altitude correction at all, which is precisely the
     * failure this tuning exists to prevent, so those are rejected rather than accepted from config.
     */
    @Test void zeroWouldMakeTheSolverInertSoItFallsBackToDefaults() {
        var fallback = LevitationTuning.DEFAULT;
        var inert = new LevitationTuning(1.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.25, 0.6, 1152, 6);
        assertEquals(fallback.inputClearanceOffset(), inert.inputClearanceOffset(), 1e-9);
        assertEquals(fallback.followGain(), inert.followGain(), 1e-9);
        assertEquals(fallback.maxClimbSpeed(), inert.maxClimbSpeed(), 1e-9);
        assertEquals(fallback.maxDescentSpeed(), inert.maxDescentSpeed(), 1e-9);
        assertEquals(fallback.inputVerticalSpeed(), inert.inputVerticalSpeed(), 1e-9);
    }

    @Test void zeroSinkSpeedIsAllowedBecauseHoldingAltitudeIsMeaningful() {
        var hovering = new LevitationTuning(1.5, 4.0, 0.4, 0.5, 0.3, 0.3, 0.0, 0.6, 1152, 6);
        assertEquals(0.0, hovering.sinkSpeed(), 1e-9);
        assertEquals(0.0, MagneticMovement.degradedVertical(0, hovering), 1e-9);
    }

    @Test void restClearanceIsNeverBelowTheClearanceFloor() {
        var tuning = new LevitationTuning(0.0, 4.0, 0.4, 0.5, 0.3, 0.3, 0.25, 0.6, 1152, 6);
        assertEquals(MagneticMovement.MIN_CLEARANCE, tuning.restClearance(), 1e-9);
    }
}
