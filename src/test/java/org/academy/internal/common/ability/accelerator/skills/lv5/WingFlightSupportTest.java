package org.academy.internal.common.ability.accelerator.skills.lv5;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WingFlightSupportTest {
    @Test
    void fanOnlyScalesBaseDamageExceptForUnchangedPlatinumWing() {
        assertEquals(46.0f, WingFlightSupport.calculateFanDamage(2.0f, 1000.0f, 3.0f, false));
        assertEquals(10.0f, WingFlightSupport.calculateFanDamage(2.0f, 1000.0f, 0.0f, false));
        assertEquals(66.0f, WingFlightSupport.calculateFanDamage(2.0f, 1000.0f, 3.0f, true));
        assertEquals(0.0f, WingFlightSupport.calculateFanDamage(2.0f, 1000.0f, 0.0f, true));
    }

    @Test
    void blackSweepUsesMeleeAttackFortyFlatDamageAndFivePercentTrueMaxHealth() {
        assertEquals(176.0f, WingFlightSupport.calculateBlackSweepDamage(2.0f, 1000.0f, 3.0f));
        assertEquals(50.0f, WingFlightSupport.calculateBlackSweepDamage(2.0f, 1000.0f, 0.0f));
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
