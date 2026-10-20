package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelMotionControllerTest {
    @Test
    void invalidDurationsDoNotStartMotionAndFiniteDurationsAreBounded() {
        for (double seconds : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0, SubLevelMotionController.durationTicks(seconds));
        }
        assertEquals(1, SubLevelMotionController.durationTicks(0.001));
        assertEquals(20, SubLevelMotionController.durationTicks(1));
        assertEquals(1_200, SubLevelMotionController.durationTicks(Double.MAX_VALUE));
    }

    @Test
    void invalidDirectionsNeverReachNativePhysics() {
        assertNull(SubLevelMotionController.normalized(new Vec3(Double.NaN, 0, 0)));
        assertNull(SubLevelMotionController.normalized(new Vec3(0, Double.POSITIVE_INFINITY, 0)));
        assertNull(SubLevelMotionController.normalized(Vec3.ZERO));
        var unit = SubLevelMotionController.normalized(new Vec3(Double.MAX_VALUE, Double.MAX_VALUE, 0));
        assertNotNull(unit);
        assertEquals(1, unit.length(), 1.0E-10);
    }

    @Test
    void moveBrakesAtArrivalInsteadOfKeepingThePreviousVelocity() {
        var position = Vec3.ZERO;
        var target = new Vec3(3, 1, -2);
        double previousDistance = target.length();
        for (int tick = 0; tick < 120; tick++) {
            var velocity = SubLevelMotionController.approachVelocity(target.subtract(position));
            assertTrue(velocity.length() <= 8.0 + 1.0E-10);
            position = position.add(velocity.scale(0.05));
            double distance = target.distanceTo(position);
            assertTrue(distance <= previousDistance);
            previousDistance = distance;
        }
        assertTrue(previousDistance < 1.0E-8);
        assertEquals(Vec3.ZERO, SubLevelMotionController.approachVelocity(Vec3.ZERO));
    }
}
