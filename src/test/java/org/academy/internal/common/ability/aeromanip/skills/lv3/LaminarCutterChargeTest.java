package org.academy.internal.common.ability.aeromanip.skills.lv3;

import net.minecraft.world.phys.Vec3;
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

    @Test
    void bladeRightAxisStaysHorizontalAndPerpendicularToCast() {
        var facingEast = LaminarCutter.Server.bladeRight(new Vec3(1.0, 0.0, 0.0));
        var facingSouthAndDown = new Vec3(0.0, -0.6, 0.8).normalize();
        var slopedRight = LaminarCutter.Server.bladeRight(facingSouthAndDown);

        assertEquals(0.0, facingEast.x, 1.0e-8);
        assertEquals(0.0, facingEast.y, 1.0e-8);
        assertEquals(1.0, facingEast.z, 1.0e-8);
        assertEquals(0.0, slopedRight.y, 1.0e-8);
        assertEquals(0.0, slopedRight.dot(facingSouthAndDown), 1.0e-8);
        assertEquals(1.0, slopedRight.length(), 1.0e-8);
    }
}
