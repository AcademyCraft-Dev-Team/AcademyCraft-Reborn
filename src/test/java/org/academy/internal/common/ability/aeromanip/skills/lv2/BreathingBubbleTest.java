package org.academy.internal.common.ability.aeromanip.skills.lv2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
