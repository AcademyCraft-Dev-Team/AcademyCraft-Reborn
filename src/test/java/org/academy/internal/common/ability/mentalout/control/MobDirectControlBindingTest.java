package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.world.phys.Vec3;
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
}
