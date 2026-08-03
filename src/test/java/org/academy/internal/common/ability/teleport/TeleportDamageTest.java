package org.academy.internal.common.ability.teleport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportDamageTest {
    @Test
    void threateningDamageUsesWeaponPlayerAndSpaceFoldingMultipliers() {
        assertEquals(7.0f, TeleportDamage.threatening(4.0f, 3.0f, 1.0f, false));
        assertEquals(17.5f, TeleportDamage.threatening(4.0f, 3.0f, 2.0f, true));
    }

    @Test
    void fleshRippingUsesTargetMaximumHealthWithoutPlayerMultiplier() {
        assertEquals(17.0f, TeleportDamage.fleshRipping(12.0f, 100.0f, false));
        assertEquals(21.25f, TeleportDamage.fleshRipping(12.0f, 100.0f, true));
    }

    @Test
    void formulasRejectNonFiniteValuesAndClampNegativeInputs() {
        assertEquals(0.0f, TeleportDamage.threatening(Float.NaN, 3.0f, 1.0f, false));
        assertEquals(0.0f, TeleportDamage.threatening(4.0f, 3.0f, Float.POSITIVE_INFINITY, true));
        assertEquals(0.0f, TeleportDamage.fleshRipping(0.0f, -100.0f, false));
    }
}
