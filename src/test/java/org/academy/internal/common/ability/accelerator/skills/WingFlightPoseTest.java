package org.academy.internal.common.ability.accelerator.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WingFlightPoseTest {
    @Test
    void keepsBoostPoseThroughTheGraceWindow() {
        assertTrue(WingFlightPose.isBoosting(100L, 100L));
        assertTrue(WingFlightPose.isBoosting(105L, 100L));
        assertFalse(WingFlightPose.isBoosting(106L, 100L));
    }

    @Test
    void rejectsMissingOrFutureBoostTicks() {
        assertFalse(WingFlightPose.isBoosting(100L, null));
        assertFalse(WingFlightPose.isBoosting(100L, 101L));
    }
}
