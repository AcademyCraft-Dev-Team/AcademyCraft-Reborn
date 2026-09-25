package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportTargetingTest {
    @Test
    void blockTopPlacementIsCenteredAndUsesEntityHeight() {
        var center = TeleportTargeting.standingCenterAbove(new BlockPos(10, 20, -4), 1.8);

        assertEquals(10.5, center.x, 0.0001);
        assertEquals(21.9, center.y, 0.0001);
        assertEquals(-3.5, center.z, 0.0001);
    }
}
