package org.academy.internal.common.ability.aeromanip.skills.lv3;

import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RejectingWindTest {
    @Test
    void chargeTiersEscalateDamageLiftAndKnockback() {
        assertEquals(3.0f, RejectingWind.baseDamage(AeromanipChargeTier.INSTANT));
        assertEquals(5.0f, RejectingWind.baseDamage(AeromanipChargeTier.HALF));
        assertEquals(7.0f, RejectingWind.baseDamage(AeromanipChargeTier.FULL));
        assertEquals(0.08, RejectingWind.verticalForce(AeromanipChargeTier.INSTANT));
        assertEquals(0.62, RejectingWind.verticalForce(AeromanipChargeTier.FULL));
        assertEquals(1.65, RejectingWind.horizontalForce(AeromanipChargeTier.FULL, false));
        assertEquals(1.98, RejectingWind.horizontalForce(AeromanipChargeTier.FULL, true), 1.0e-9);
    }
}
