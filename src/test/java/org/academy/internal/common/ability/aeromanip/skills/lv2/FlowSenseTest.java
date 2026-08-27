package org.academy.internal.common.ability.aeromanip.skills.lv2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowSenseTest {
    @Test
    void sneakingPlayersRemainHiddenUntilFinalMilestone() {
        assertTrue(FlowSense.canSensePlayer(false, false));
        assertFalse(FlowSense.canSensePlayer(true, false));
        assertTrue(FlowSense.canSensePlayer(true, true));
    }

    @Test
    void firstTwoMilestonesImproveRangeAndCadence() {
        assertEquals(24.0, FlowSense.sensingRange(false));
        assertEquals(32.0, FlowSense.sensingRange(true));
        assertEquals(10, FlowSense.sensingInterval(false));
        assertEquals(5, FlowSense.sensingInterval(true));
    }
}
