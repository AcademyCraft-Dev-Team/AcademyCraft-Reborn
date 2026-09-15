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
}
