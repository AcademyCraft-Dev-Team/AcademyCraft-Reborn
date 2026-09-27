package org.academy.internal.common.ability.electromaster.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElectromagneticShieldTest {
    @Test
    void absorbsDamageUntilCapacityIsFull() {
        var first = ElectromagneticShield.absorbDamage(0, 100, 35);
        assertEquals(35, first.storedDamage());
        assertEquals(0, first.remainingDamage());

        var overflow = ElectromagneticShield.absorbDamage(90, 100, 25);
        assertEquals(100, overflow.storedDamage());
        assertEquals(15, overflow.remainingDamage());
    }

    @Test
    void clampsStoredDamageWhenCapacityShrinks() {
        var result = ElectromagneticShield.absorbDamage(120, 80, 5);

        assertEquals(80, result.storedDamage());
        assertEquals(5, result.remainingDamage());
    }

    @Test
    void coolingNeverProducesNegativeStoredDamage() {
        assertEquals(15, ElectromagneticShield.coolStoredDamage(25, 10));
        assertEquals(0, ElectromagneticShield.coolStoredDamage(5, 10));
    }
}
