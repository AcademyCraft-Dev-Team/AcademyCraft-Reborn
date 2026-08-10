package org.academy.internal.common.ability.accelerator.skills.lv5;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WingFlightSupportTest {
    @Test
    void keepsReferenceCombatConstants() {
        assertEquals(32.0, WingFlightSupport.ATTACK_RANGE);
        assertEquals(0.35, WingFlightSupport.FAN_COS_THRESHOLD);
        assertEquals(0.01f, WingFlightSupport.MAX_HEALTH_DAMAGE_RATIO);
        assertEquals(10.0f, WingFlightSupport.FIXED_DAMAGE);
    }

    @Test
    void acceptsTargetsInsideTheForwardFan() {
        assertTrue(WingFlightSupport.isInFan(
                Vec3.ZERO,
                new Vec3(0, 0, 1),
                new Vec3(0, 0, 16),
                WingFlightSupport.ATTACK_RANGE,
                WingFlightSupport.FAN_COS_THRESHOLD
        ));
        assertTrue(WingFlightSupport.isInFan(
                Vec3.ZERO,
                new Vec3(0, 0, 1),
                new Vec3(Math.sqrt(1.0 - 0.35 * 0.35), 0, 0.35),
                WingFlightSupport.ATTACK_RANGE,
                WingFlightSupport.FAN_COS_THRESHOLD
        ));
    }

    @Test
    void rejectsTargetsBehindOutsideRangeOrWithoutDirection() {
        assertFalse(WingFlightSupport.isInFan(
                Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 0, -1), 32, 0.35));
        assertFalse(WingFlightSupport.isInFan(
                Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 0, 32.01), 32, 0.35));
        assertFalse(WingFlightSupport.isInFan(
                Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, 1), 32, 0.35));
        assertFalse(WingFlightSupport.isInFan(
                Vec3.ZERO, new Vec3(0, 0, 1), Vec3.ZERO, 32, 0.35));
    }
}
