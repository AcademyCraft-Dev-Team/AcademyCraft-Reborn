package org.academy.internal.common.ability.mentalout.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
