package org.academy.internal.common.ability.aeromanip.skills.lv4;

import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VortexPullTest {
    @Test
    void chargeTiersEscalateRadiusStrengthAndLifetime() {
        assertEquals(7.0, VortexPull.baseRadius(AeromanipChargeTier.INSTANT));
        assertEquals(8.0, VortexPull.baseRadius(AeromanipChargeTier.HALF));
        assertEquals(12.0, VortexPull.baseRadius(AeromanipChargeTier.FULL));
        assertTrue(VortexPull.baseStrength(AeromanipChargeTier.FULL)
                > VortexPull.baseStrength(AeromanipChargeTier.HALF));
        assertEquals(1, VortexPull.baseDuration(AeromanipChargeTier.INSTANT));
        assertEquals(80, VortexPull.baseDuration(AeromanipChargeTier.HALF));
        assertEquals(120, VortexPull.baseDuration(AeromanipChargeTier.FULL));
    }
}
