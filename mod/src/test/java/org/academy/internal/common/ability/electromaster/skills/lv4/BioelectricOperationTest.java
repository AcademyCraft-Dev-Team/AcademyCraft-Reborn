package org.academy.internal.common.ability.electromaster.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BioelectricOperationTest {
    @Test
    void attackDamageScalesWithAbilityPower() {
        assertEquals(4.0, BioelectricOperation.getAttackDamageBonus(1.0f));
        assertEquals(6.0, BioelectricOperation.getAttackDamageBonus(1.5f));
        assertEquals(0.0, BioelectricOperation.getAttackDamageBonus(-1.0f));
    }
}
