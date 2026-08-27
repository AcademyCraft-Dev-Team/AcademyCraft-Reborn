package org.academy.internal.common.ability.aeromanip.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightTest {
    @Test
    void everyMilestoneRaisesCreativeFlightSpeed() {
        assertEquals(0.035f, Flight.flyingSpeed(0));
        assertEquals(0.05f, Flight.flyingSpeed(1));
        assertEquals(0.065f, Flight.flyingSpeed(2));
        assertEquals(0.08f, Flight.flyingSpeed(3));
        assertEquals(0.08f, Flight.flyingSpeed(99));
    }
}
