package org.academy.internal.common.ability.teleport;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportSafetyTest {
    @Test
    void finiteCheckRejectsInvalidPacketCoordinates() {
        assertTrue(TeleportSafety.isFinite(new Vec3(1, 2, 3)));
        assertFalse(TeleportSafety.isFinite(new Vec3(Double.NaN, 2, 3)));
        assertFalse(TeleportSafety.isFinite(new Vec3(1, Double.POSITIVE_INFINITY, 3)));
    }
}
