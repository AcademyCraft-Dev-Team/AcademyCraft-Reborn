package org.academy.internal.common.ability.aeromanip.skills.lv1;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirflowJetTest {
    @Test
    void vectorTravelAccumulatesAsMaceFallMomentum() {
        assertEquals(7.0, AirflowJet.Server.accumulateMaceMomentum(
                2.0,
                5.0,
                new Vec3(0.0, 0.0, 2.0)
        ));
        assertEquals(4.0, AirflowJet.Server.accumulateMaceMomentum(
                4.0,
                1.0,
                new Vec3(Double.NaN, 0.0, 0.0)
        ));
    }

    @Test
    void chargeMilestonesImproveTheMatchingReleaseTier() {
        assertEquals(1.5, AirflowJet.instantDamage(false), 1.0E-9);
        assertEquals(2.0, AirflowJet.instantDamage(true), 1.0E-9);
        assertEquals(1.35, AirflowJet.halfLaunchSpeed(false), 1.0E-9);
        assertEquals(1.62, AirflowJet.halfLaunchSpeed(true), 1.0E-9);
        assertEquals(30, AirflowJet.fullPropulsionDuration(false));
        assertEquals(40, AirflowJet.fullPropulsionDuration(true));
    }

    @Test
    void fullFluidSubmersionReducesFullChargePropulsionSpeed() {
        assertEquals(2.35, AirflowJet.fullPropulsionSpeed(false, false), 1.0E-9);
        assertEquals(2.82, AirflowJet.fullPropulsionSpeed(true, false), 1.0E-9);
        assertEquals(0.94, AirflowJet.fullPropulsionSpeed(false, true), 1.0E-9);
    }
}
