package org.academy.internal.common.ability.aeromanip.skills.lv1;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaminarBufferTest {
    @Test
    void bufferSlowsFallingAndMatchesStandardGroundMovementSpeed() {
        var movementInput = LaminarBuffer.horizontalMovementInput(0.0f, 1.0f, 0.0f);
        var groundSpeed = LaminarBuffer.groundEquivalentHorizontalSpeed(
                0.1, false, movementInput.horizontalDistance());
        var result = LaminarBuffer.bufferedAirVelocity(
                new Vec3(0.04, -0.7, 0.0), movementInput, groundSpeed);

        assertEquals(0.0, result.x, 1.0e-9);
        assertEquals(-0.12, result.y, 1.0e-9);
        assertEquals(0.1 / (1.0 - 0.54600006) - 0.02, result.z, 1.0e-9);
    }

    @Test
    void bufferCancelsAirDragWhenThereIsNoMovementInput() {
        var result = LaminarBuffer.bufferedAirVelocity(
                new Vec3(0.182, -0.2, -0.091), Vec3.ZERO, 0.0);

        assertEquals(0.2, result.x, 1.0e-9);
        assertEquals(-0.12, result.y, 1.0e-9);
        assertEquals(-0.1, result.z, 1.0e-9);
    }

    @Test
    void bufferDoesNotReduceMomentumThatAlreadyExceedsGroundSpeed() {
        var movementInput = LaminarBuffer.horizontalMovementInput(1.0f, 0.0f, 0.0f);
        var result = LaminarBuffer.bufferedAirVelocity(
                new Vec3(0.6, -0.2, 0.0), movementInput, 0.2);

        assertEquals(0.6, result.x, 1.0e-9);
        assertEquals(-0.12, result.y, 1.0e-9);
        assertEquals(0.0, result.z, 1.0e-9);
    }

    @Test
    void jumpAndDurationMilestonesApplyToTheirOwnEffects() {
        var result = LaminarBuffer.boostedJumpVelocity(new Vec3(0.2, 0.42, 0.0));
        assertEquals(0.21, result.x, 1.0e-9);
        assertEquals(0.54, result.y, 1.0e-9);
        assertEquals(60, LaminarBuffer.hoverDuration(false));
        assertEquals(100, LaminarBuffer.hoverDuration(true));
        assertEquals(200, LaminarBuffer.platformDuration(false));
        assertEquals(300, LaminarBuffer.platformDuration(true));
    }
}
