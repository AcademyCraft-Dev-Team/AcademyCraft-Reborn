package org.academy.internal.common.ability.electromaster.skills.lv3;

import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MagneticWeaponTest {
    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-9);
        assertEquals(expected.y, actual.y, 1.0E-9);
        assertEquals(expected.z, actual.z, 1.0E-9);
    }

    @Test
    void damageUsesAttackAndAbilityScaling() {
        assertEquals(6.0f, MagneticWeapon.Server.calculateDamage(10.0f, 1.0f), 0.0001f);
        assertEquals(9.0f, MagneticWeapon.Server.calculateDamage(10.0f, 1.5f), 0.0001f);
        assertEquals(0.0f, MagneticWeapon.Server.calculateDamage(-1.0f, 1.0f), 0.0001f);
    }

    @Test
    void maceUsesRemoteAttackDistanceAsSyntheticFallHeight() {
        assertEquals(0.0f, MagneticWeapon.maceFallDistance(-2.0), 0.0001f);
        assertEquals(7.25f, MagneticWeapon.maceFallDistance(7.25), 0.0001f);
        assertEquals(0.0f, MagneticWeapon.maceFallDistance(Double.NaN), 0.0001f);
    }

    @Test
    void synchronizedDataCodecPreservesWeaponVisibilityState() {
        var expected = new MagneticWeapon.Data(true, 6, true);
        var buffer = Unpooled.buffer();

        MagneticWeapon.Data.CODEC.encode(buffer, expected);

        assertEquals(expected, MagneticWeapon.Data.CODEC.decode(buffer));
    }

    @Test
    void attackTimelineClaimsImpactExactlyOnce() {
        var timeline = new MagneticWeaponAttackTimeline();

        while (timeline.attackTick() < MagneticWeaponBladeMotion.IMPACT_TICK) {
            timeline.advance();
        }

        assertTrue(timeline.claimImpact());
        assertFalse(timeline.claimImpact());
        while (!timeline.isFinished()) timeline.advance();
        assertTrue(timeline.isFinished());
    }

    @Test
    void cancelledAttackNeverClaimsImpact() {
        var timeline = new MagneticWeaponAttackTimeline();
        timeline.cancel();
        while (timeline.attackTick() < MagneticWeaponBladeMotion.IMPACT_TICK) timeline.advance();

        assertFalse(timeline.claimImpact());
    }

    @Test
    void bladeMotionUsesExpectedPhaseEndpointsAndAlternatingSides() {
        var origin = Vec3.ZERO;
        var target = new Vec3(0.0, 1.0, 10.0);
        var idle = new Vec3(1.0, 1.5, -1.0);

        var impact = MagneticWeaponBladeMotion.sample(origin, target, idle, 5, 0);
        var returned = MagneticWeaponBladeMotion.sample(origin, target, idle, 10, 0);
        var left = MagneticWeaponBladeMotion.sample(origin, target, idle, 4, 0);
        var right = MagneticWeaponBladeMotion.sample(origin, target, idle, 4, 1);

        assertVecEquals(target, impact.position());
        assertVecEquals(idle, returned.position());
        assertEquals(-left.position().x, right.position().x, 1.0E-9);
        assertTrue(Math.signum(left.position().x) != Math.signum(right.position().x));
        for (var tick = 1; tick <= MagneticWeaponBladeMotion.ATTACK_END_TICK; tick++) {
            var motion = MagneticWeaponBladeMotion.sample(origin, target, idle, tick, 2);
            assertTrue(Double.isFinite(motion.position().x));
            assertTrue(Double.isFinite(motion.position().y));
            assertTrue(Double.isFinite(motion.position().z));
            assertTrue(Double.isFinite(motion.tangent().lengthSqr()));
        }
    }
}
