package org.academy.internal.common.ability.electromaster.skills.lv3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MagneticWeaponTest {
    @Test
    void damageUsesAttackAndAbilityScaling() {
        assertEquals(6.0f, MagneticWeapon.Server.calculateDamage(10.0f, 1.0f), 0.0001f);
        assertEquals(9.0f, MagneticWeapon.Server.calculateDamage(10.0f, 1.5f), 0.0001f);
        assertEquals(0.0f, MagneticWeapon.Server.calculateDamage(-1.0f, 1.0f), 0.0001f);
    }
}
