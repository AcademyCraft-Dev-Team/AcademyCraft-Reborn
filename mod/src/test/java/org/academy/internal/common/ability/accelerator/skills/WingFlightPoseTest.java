package org.academy.internal.common.ability.accelerator.skills;

import net.minecraft.world.phys.Vec3;
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

    @Test
    void keepsTheSlowFlightPoseWhileMomentumRemains() {
        assertTrue(WingFlightPose.isCoasting(new Vec3(0.02, 0.0, 0.0)));
        assertTrue(WingFlightPose.isCoasting(new Vec3(0.0, -0.2, 0.0)));
        assertFalse(WingFlightPose.isCoasting(new Vec3(0.0, -0.08, 0.0)));
        assertFalse(WingFlightPose.isCoasting(new Vec3(0.009, 0.0, 0.0)));
        assertFalse(WingFlightPose.isCoasting(new Vec3(Double.NaN, 0.0, 0.0)));
    }
}
