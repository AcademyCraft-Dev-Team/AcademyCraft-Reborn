package org.academy.internal.common.ability.aeromanip.skills.lv1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirCushionTest {
    @Test
    void eachSkillLevelProducesThePromisedFallDamageReduction() {
        assertEquals(0.30f, AirCushion.Events.damageMultiplier(0), 0.0001f);
        assertEquals(0.15f, AirCushion.Events.damageMultiplier(1), 0.0001f);
        assertEquals(0.0f, AirCushion.Events.damageMultiplier(2), 0.0001f);
    }

    @Test
    void invalidLevelsAreSafelyClamped() {
        assertEquals(0.30f, AirCushion.Events.damageMultiplier(-10), 0.0001f);
        assertEquals(0.0f, AirCushion.Events.damageMultiplier(10), 0.0001f);
    }
}
