package org.academy.internal.common.ability.aeromanip.skills.lv2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreathingBubbleTest {
    @Test
    void compressedAirCostTracksEfficiencyAndSharingMilestones() {
        assertEquals(4.0f, BreathingBubble.compressedAirCost(0, false));
        assertEquals(6.0f, BreathingBubble.compressedAirCost(0, true));
        assertEquals(3.0f, BreathingBubble.compressedAirCost(1, false));
        assertEquals(5.0f, BreathingBubble.compressedAirCost(2, true));
    }

    @Test
    void finalMilestoneExtendsActiveCastRadius() {
        assertEquals(16.0, BreathingBubble.activeRadius(2));
        assertEquals(24.0, BreathingBubble.activeRadius(3));
    }

    @Test
    void passiveVfxCanAppearAtMostOnceEveryTenSeconds() {
        assertFalse(BreathingBubble.passiveVfxCooldownElapsed(199L, 0L));
        assertTrue(BreathingBubble.passiveVfxCooldownElapsed(200L, 0L));
        assertTrue(BreathingBubble.passiveVfxCooldownElapsed(10L, 200L));
    }
}
