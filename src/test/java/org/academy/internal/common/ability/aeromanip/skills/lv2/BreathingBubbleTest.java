package org.academy.internal.common.ability.aeromanip.skills.lv2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BreathingBubbleTest {
    @Test
    void activationCostDecreasesAtFirstMilestone() {
        assertEquals(8.0f, BreathingBubble.activationAirCost(0));
        assertEquals(6.0f, BreathingBubble.activationAirCost(1));
        assertEquals(6.0f, BreathingBubble.activationAirCost(2));
    }

    @Test
    void followingDryPocketExpandsAtFinalMilestone() {
        assertEquals(2.0, BreathingBubble.activeRadius(0));
        assertEquals(2.0, BreathingBubble.activeRadius(2));
        assertEquals(3.0, BreathingBubble.activeRadius(3));
    }
}
