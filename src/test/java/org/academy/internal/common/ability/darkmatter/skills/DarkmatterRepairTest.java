package org.academy.internal.common.ability.darkmatter.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterRepairTest {
    @Test
    void healingScalesWithAbilityPower() {
        assertEquals(4.0f, DarkmatterRepair.Server.healAmount(1), 0.0001f);
        assertEquals(6.0f, DarkmatterRepair.Server.healAmount(1.5f), 0.0001f);
    }
}
