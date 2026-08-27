package org.academy.internal.common.ability.aeromanip.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HighSpeedJetTest {
    @Test
    void milestonesExpandNozzleCapacityAndDuration() {
        assertEquals(8, HighSpeedJet.maximumNozzles(0));
        assertEquals(12, HighSpeedJet.maximumNozzles(1));
        assertEquals(40, HighSpeedJet.activationDuration(1));
        assertEquals(60, HighSpeedJet.activationDuration(2));
    }

    @Test
    void activationCostsScaleWithTheNumberOfNozzles() {
        assertEquals(8.0f, HighSpeedJet.activationCpCost(0));
        assertEquals(16.0f, HighSpeedJet.activationCpCost(4));
        assertEquals(32.0f, HighSpeedJet.activationAirCost(4));
        assertEquals(0.0f, HighSpeedJet.activationAirCost(-1));
    }
}
