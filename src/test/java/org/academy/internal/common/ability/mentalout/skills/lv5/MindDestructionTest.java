package org.academy.internal.common.ability.mentalout.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MindDestructionTest {
    @Test
    void eachPulseDealsOnePercentMaximumHealthPlusTen() {
        assertEquals(10.0f, MindDestruction.damagePerPulse(0.0f));
        assertEquals(11.0f, MindDestruction.damagePerPulse(100.0f));
        assertEquals(20.0f, MindDestruction.damagePerPulse(1000.0f));
    }

    @Test
    void tenOneSecondPulsesFillTheTenSecondDuration() {
        assertEquals(200, MindDestruction.DURATION_TICKS);
        assertEquals(20, MindDestruction.DAMAGE_INTERVAL_TICKS);
        assertEquals(10, MindDestruction.DURATION_TICKS / MindDestruction.DAMAGE_INTERVAL_TICKS);
    }
}
