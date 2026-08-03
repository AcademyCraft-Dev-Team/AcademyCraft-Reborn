package org.academy.internal.common.ability.electromaster.skills.lv3;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThunderLanceTest {
    @Test
    void quickModeUsesReferenceDamageAndCurrentMultipliers() {
        assertEquals(16.0f, ThunderLance.calculateQuickDamage(1.0f, 1.0f));
        assertEquals(36.0f, ThunderLance.calculateQuickDamage(1.5f, 1.5f));
        assertEquals(0.0f, ThunderLance.calculateQuickDamage(-1.0f, 1.0f));
    }

    @Test
    void handPositionUsesAStableFallbackWhenLookingStraightUp() {
        var hand = ThunderLance.calculateHandPosition(Vec3.ZERO, new Vec3(0, 1, 0));

        assertEquals(0.4, hand.x, 1.0e-9);
        assertEquals(1.7, hand.y, 1.0e-9);
        assertEquals(0.0, hand.z, 1.0e-9);
    }
}
