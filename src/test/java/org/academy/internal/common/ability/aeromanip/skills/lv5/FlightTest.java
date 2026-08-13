package org.academy.internal.common.ability.aeromanip.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightTest {
    @Test
    void accelerationCostTracksFlightSprintAndJetActivity() {
        assertFalse(Flight.consumesAccelerationCp(false, true, true, true, 1.0));
        assertFalse(Flight.consumesAccelerationCp(true, false, false, false, 0.0));
        assertFalse(Flight.consumesAccelerationCp(true, false, false, false, 1.0));
        assertFalse(Flight.consumesAccelerationCp(true, false, true, false, 0.1));
        assertFalse(Flight.consumesAccelerationCp(true, false, false, true, 0.1));
        assertTrue(Flight.consumesAccelerationCp(true, true, true, false, 0.1));
        assertTrue(Flight.consumesAccelerationCp(true, true, false, true, 0.1));
        assertFalse(Flight.consumesAccelerationCp(true, true, false, false, 0.7));
        assertTrue(Flight.consumesAccelerationCp(true, true, false, false, 0.71));
    }
}
