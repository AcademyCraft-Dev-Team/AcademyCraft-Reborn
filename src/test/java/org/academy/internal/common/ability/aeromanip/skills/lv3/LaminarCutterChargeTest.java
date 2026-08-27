package org.academy.internal.common.ability.aeromanip.skills.lv3;

import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaminarCutterChargeTest {
    @Test
    void laterChargeTiersWidenAndStrengthenTheBlade() {
        assertEquals(2.5, LaminarCutter.Server.bladeHalfWidth(AeromanipChargeTier.INSTANT));
        assertEquals(4.0, LaminarCutter.Server.bladeHalfWidth(AeromanipChargeTier.HALF));
        assertEquals(4.0f, LaminarCutter.Server.baseDamage(AeromanipChargeTier.INSTANT));
        assertEquals(6.0f, LaminarCutter.Server.baseDamage(AeromanipChargeTier.HALF));
        assertEquals(8.0f, LaminarCutter.Server.baseDamage(AeromanipChargeTier.FULL));
        assertEquals(0.55, LaminarCutter.Server.knockback(AeromanipChargeTier.INSTANT));
        assertEquals(1.25, LaminarCutter.Server.knockback(AeromanipChargeTier.FULL));
    }
}
