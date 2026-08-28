package org.academy.internal.common.ability.aeromanip.skills.lv1;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaminarBufferTest {
    @Test
    void bufferOnlyLimitsFallingAndLeavesHorizontalVelocityUntouched() {
        var result = LaminarBuffer.bufferedAirVelocity(new Vec3(0.6, -0.7, -0.25));

        assertEquals(0.6, result.x, 1.0e-9);
        assertEquals(-0.12, result.y, 1.0e-9);
        assertEquals(-0.25, result.z, 1.0e-9);
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
