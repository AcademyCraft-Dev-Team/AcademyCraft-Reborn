package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.PlayerControlFrame;
import org.academy.api.common.entitycontrol.PlayerMovementMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobDirectControlBindingTest {
    @Test
    void groundMobJumpDoesNotReceiveSustainedVerticalVelocity() {
        assertEquals(0.0, MobDirectControlBinding.aquaticVerticalInput(false, true, false));
        assertEquals(0.0, MobDirectControlBinding.aquaticVerticalInput(false, false, true));
    }

    @Test
    void swimmingMobCanStillAscendAndDescend() {
        assertEquals(0.12, MobDirectControlBinding.aquaticVerticalInput(true, true, false));
        assertEquals(-0.12, MobDirectControlBinding.aquaticVerticalInput(true, false, true));
        assertEquals(0.0, MobDirectControlBinding.aquaticVerticalInput(true, true, true));
    }

    @Test
    void sprintRequiresBothInputAndHorizontalMovement() {
        assertTrue(MobDirectControlBinding.shouldSprint(true, new Vec3(0.0, 0.0, 1.0)));
        assertFalse(MobDirectControlBinding.shouldSprint(false, new Vec3(0.0, 0.0, 1.0)));
        assertFalse(MobDirectControlBinding.shouldSprint(true, new Vec3(0.0, 1.0, 0.0)));
    }

    @Test
    void freeFlyingMountFollowsRiderPitchWhileGroundMountStaysHorizontal() {
        var frame = new PlayerControlFrame(
                1.0f, 0.0f, 0.0f, 45.0f,
                false, false, false, false, false, PlayerMovementMode.FLY
        );
        var flying = MobDirectControlBinding.movementInput(frame, true);
        var grounded = MobDirectControlBinding.movementInput(frame, false);

        assertTrue(flying.y < -0.5, "Looking down should descend while flying forward");
        assertTrue(flying.z > 0.5);
        assertEquals(0.0, grounded.y, 1.0e-8);
        assertEquals(1.0, grounded.z, 1.0e-8);
    }
}
