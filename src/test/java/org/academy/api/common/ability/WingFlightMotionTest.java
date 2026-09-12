package org.academy.api.common.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WingFlightMotionTest {
    private static final WingControlIntent RELEASED = new WingControlIntent(0, 0, 0);

    @Test
    void zeroMomentumStopsEveryAxisImmediatelyAndKeepsHovering() {
        var moving = new Vec3(5, -2, 3);
        assertEquals(Vec3.ZERO, WingFlightMotion.step(moving, RELEASED, 16, 0, 1));
        assertEquals(Vec3.ZERO, WingFlightMotion.step(moving, RELEASED, 0, 0, 1));
    }

    @Test
    void momentumPercentageIsAppliedOnceOnRelease() {
        var moving = new Vec3(4, 0, -2);
        var full = WingFlightMotion.step(moving, RELEASED, 1, 1, 1);
        var half = WingFlightMotion.step(moving, RELEASED, 1, 0.5f, 1);
        assertEquals(full.scale(0.5), half);
        assertEquals(half.multiply(0.995, 0, 0.995), WingFlightMotion.step(half, RELEASED, 0, 0.5f, 1));
        assertEquals(new Vec3(3.98, 0, -1.99), full);
    }

    @Test
    void heldDirectionsAreUnaffectedByMomentumAndOpposingKeysHover() {
        var input = new WingControlIntent(5, 30, -20);
        assertEquals(WingFlightMotion.step(Vec3.ZERO, input, 0, 1, 1),
                WingFlightMotion.step(Vec3.ZERO, input, 0, 0, 1));
        assertEquals(Vec3.ZERO, WingFlightMotion.step(new Vec3(5, 2, 1),
                new WingControlIntent(15, 0, 0), 5, 0, 1));
    }

    @Test
    void noServerUpdatesAreNeededToStopAndHoverForHundredsOfLocalFrames() {
        var velocity = new Vec3(10, -10, 10);
        for (int frame = 0; frame < 400; frame++) {
            velocity = WingFlightMotion.step(velocity, RELEASED, frame == 0 ? 16 : 0, 0, 1);
            assertEquals(Vec3.ZERO, velocity);
        }
    }

    @Test
    void invalidSettingsCannotProduceNaNOrReverseMomentum() {
        assertEquals(1, WingFlightMotion.clampMomentum(Float.NaN));
        assertEquals(1, WingFlightMotion.clampMomentum(Float.POSITIVE_INFINITY));
        assertEquals(0, WingFlightMotion.clampMomentum(-1));
        assertEquals(1, WingFlightMotion.clampMomentum(2));
    }
}
