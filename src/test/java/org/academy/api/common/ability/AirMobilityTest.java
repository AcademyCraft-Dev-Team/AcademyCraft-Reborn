package org.academy.api.common.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AirMobilityTest {
    @Test
    void hoverPreservesHorizontalSpeedAndTurning() {
        var velocity = new Vec3(2.4, -0.8, -1.7);
        assertEquals(new Vec3(2.4, 0.175, -1.7), AirMobility.supportedVelocity(velocity, AirMobility.HOVER, 0.5));
    }

    @Test
    void slowFallDoesNotChangeAscentOrHorizontalMomentum() {
        var ascent = new Vec3(-1.0, 0.8, 2.0);
        assertEquals(ascent, AirMobility.supportedVelocity(ascent, AirMobility.SLOW_FALL, 0.0));
        assertEquals(new Vec3(-1.0, -0.12, 2.0),
                AirMobility.supportedVelocity(new Vec3(-1.0, -2.0, 2.0), AirMobility.SLOW_FALL, 0.0));
    }

    @Test
    void disabledSupportPreservesDeliberateDownwardPropulsion() {
        var velocity = new Vec3(1.0, -2.35, 0.5);
        assertEquals(velocity, AirMobility.supportedVelocity(velocity, AirMobility.NONE, 3.0));
    }
}
