package org.academy.internal.common.ability.electromaster.skills.lv3.magneticweapon;

import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.skill.BladeTransform;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion.BladePath;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion.Phase;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion.Sweep;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion.Timing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MagneticWeaponTest {
    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 TARGET = new Vec3(0.0, 1.0, 10.0);
    private static final Vec3 IDLE = new Vec3(1.0, 1.5, -1.0);
    private static final Sweep SWEEP = new Sweep(1.0f, 12.0f, 110.0f);

    private static void assertVecEquals(Vec3 expected, Vec3 actual, double delta) {
        assertEquals(expected.x, actual.x, delta);
        assertEquals(expected.y, actual.y, delta);
        assertEquals(expected.z, actual.z, delta);
    }

    private static BladePath buildPath() {
        return MagneticWeaponBladeMotion.build(ORIGIN, TARGET, IDLE, SWEEP, null, null);
    }

    private static Vec3 facingAt(BladePath path, Timing timing, double elapsed) {
        var transform = MagneticWeaponBladeMotion.sample(
                path, SWEEP, MagneticWeaponBladeMotion.progressAt(elapsed, timing, path));
        return Vec3.directionFromRotation(transform.pitch(), transform.yaw());
    }

    @Test
    void damageUsesAttackAndAbilityScaling() {
        assertEquals(6.0f, MagneticWeapon.Server.calculateDamage(10.0f, 1.0f), 0.0001f);
        assertEquals(9.0f, MagneticWeapon.Server.calculateDamage(10.0f, 1.5f), 0.0001f);
        assertEquals(0.0f, MagneticWeapon.Server.calculateDamage(-1.0f, 1.0f), 0.0001f);
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
        var timeline = new MagneticWeaponAttackTimeline(new Timing(5, 5));

        while (timeline.elapsed() < timeline.impactElapsed()) {
            timeline.advance();
        }

        assertTrue(timeline.claimImpact());
        assertFalse(timeline.claimImpact());
        while (!timeline.isFinished()) timeline.advance();
        assertTrue(timeline.isFinished());
    }

    @Test
    void cancelledAttackNeverClaimsImpact() {
        var timeline = new MagneticWeaponAttackTimeline(new Timing(5, 5));
        timeline.cancel();
        while (timeline.elapsed() < timeline.impactElapsed()) timeline.advance();

        assertFalse(timeline.claimImpact());
    }

    @Test
    void attackTimingScalesWithDistance() {
        var near = Timing.forDistance(2.0);
        var far = Timing.forDistance(16.0);

        assertEquals(MagneticWeaponBladeMotion.MIN_STRIKE_TICKS, near.strikeTicks());
        assertTrue(far.strikeTicks() > near.strikeTicks());
        assertTrue(far.totalTicks() > far.strikeTicks());
    }

    @Test
    void pathArrivesExactlyAtTargetOnImpact() {
        var path = buildPath();
        assertNotNull(path);

        assertVecEquals(TARGET, path.impactPosition(), 1.0E-6);
        assertVecEquals(TARGET, MagneticWeaponBladeMotion.position(path, path.impactMark()), 1.0E-6);
    }

    @Test
    void progressStaysMonotoneAndReachesImpactAndEnd() {
        var path = buildPath();
        assertNotNull(path);
        var timing = new Timing(6, 8);

        assertEquals(0.0, MagneticWeaponBladeMotion.progressAt(0.0, timing, path), 1.0E-9);
        assertEquals(path.marks().get(1),
                MagneticWeaponBladeMotion.progressAt(MagneticWeaponBladeMotion.PREP_TICKS, timing, path), 1.0E-6);
        assertEquals(path.impactMark(),
                MagneticWeaponBladeMotion.progressAt(timing.strikeTicks(), timing, path), 1.0E-6);
        assertEquals(path.endMark(),
                MagneticWeaponBladeMotion.progressAt(timing.totalTicks(), timing, path), 1.0E-6);
        assertEquals(path.endMark(),
                MagneticWeaponBladeMotion.progressAt(timing.totalTicks() + 10.0, timing, path), 1.0E-6);

        var previous = 0.0;
        for (var elapsed = 0.0; elapsed <= timing.totalTicks() + 3.0; elapsed += 0.25) {
            var progress = MagneticWeaponBladeMotion.progressAt(elapsed, timing, path);
            assertTrue(progress >= previous - 1.0E-9, "progress regressed at elapsed=" + elapsed);
            previous = progress;
        }
    }

    @Test
    void sampledBladeTransformsStayFiniteAndOnCurve() {
        var path = buildPath();
        assertNotNull(path);

        var endMark = path.endMark();
        for (var step = 0; step <= 40; step++) {
            var progress = endMark * step / 40.0;
            var transform = MagneticWeaponBladeMotion.sample(path, SWEEP, progress);
            var curvePoint = MagneticWeaponBladeMotion.position(path, progress);
            assertTrue(Double.isFinite(transform.position().x));
            assertTrue(Double.isFinite(transform.position().y));
            assertTrue(Double.isFinite(transform.position().z));
            assertTrue(Float.isFinite(transform.yaw()));
            assertTrue(Float.isFinite(transform.pitch()));
            assertTrue(Float.isFinite(transform.roll()));
            assertVecEquals(curvePoint, transform.position(), 1.0E-6);
        }
    }

    @Test
    void sampledBladeOrientationStaysContinuous() {
        var path = buildPath();
        assertNotNull(path);
        var timing = new Timing(6, 8);

        var end = timing.totalTicks() + MagneticWeaponBladeMotion.RETURN_GRACE_TICKS + 0.5;
        var previous = facingAt(path, timing, 0.0);
        for (var elapsed = 1.0 / 60.0; elapsed <= end; elapsed += 1.0 / 60.0) {
            var current = facingAt(path, timing, elapsed);
            var dot = Math.max(-1.0, Math.min(1.0, previous.dot(current)));
            assertTrue(Math.acos(dot) < 0.6, "orientation jump at elapsed=" + elapsed);
            previous = current;
        }
    }

    @Test
    void settleBlendReturnsToIdle() {
        var path = buildPath();
        assertNotNull(path);

        var end = MagneticWeaponBladeMotion.sample(path, SWEEP, path.endMark());
        var settled = BladeTransform.lerp(end, MagneticWeaponBladeMotion.idleTransform(IDLE), 1.0);

        assertVecEquals(IDLE, settled.position(), 1.0E-6);
        assertEquals(MagneticWeaponBladeMotion.IDLE_ROLL, settled.roll(), 1.0E-4f);
        assertTrue(end.position().distanceTo(settled.position()) < 2.0);
    }

    @Test
    void routedWaypointsStayInsidePath() {
        var strikeRoute = List.of(ORIGIN, new Vec3(3.0, 2.0, 5.0), TARGET);
        var returnRoute = List.of(TARGET, new Vec3(-3.0, 2.0, 5.0), IDLE);
        var path = MagneticWeaponBladeMotion.build(ORIGIN, TARGET, IDLE, SWEEP, strikeRoute, returnRoute);
        assertNotNull(path);

        var halfway = MagneticWeaponBladeMotion.position(path, path.impactMark() * 0.5);
        assertTrue(halfway.z > 1.0);
        assertVecEquals(TARGET, MagneticWeaponBladeMotion.position(path, path.impactMark()), 1.0E-6);
        assertVecEquals(IDLE, MagneticWeaponBladeMotion.position(path, path.endMark()), 1.0E-6);
    }

    @Test
    void pathSurvivesSyncRoundTrip() {
        var strikeRoute = List.of(ORIGIN, new Vec3(3.0, 2.0, 5.0), TARGET);
        var returnRoute = List.of(TARGET, new Vec3(-3.0, 2.0, 5.0), IDLE);
        var path = MagneticWeaponBladeMotion.build(ORIGIN, TARGET, IDLE, SWEEP, strikeRoute, returnRoute);
        assertNotNull(path);

        var packed = MagneticWeaponBladeMotion.packPath(path);
        assertNotNull(packed);
        var restored = MagneticWeaponBladeMotion.unpackPath(packed);
        assertNotNull(restored);

        assertEquals(path.knots().size(), restored.knots().size());
        assertEquals(path.impactIndex(), restored.impactIndex());
        assertEquals(path.impactMark(), restored.impactMark(), 1.0E-12);
        assertEquals(path.endMark(), restored.endMark(), 1.0E-12);
        for (var index = 0; index < path.knots().size(); index++) {
            assertVecEquals(path.knots().get(index), restored.knots().get(index), 1.0E-9);
        }
        for (var index = 0; index < path.marks().size(); index++) {
            assertEquals(path.marks().get(index), restored.marks().get(index), 1.0E-12);
        }
    }

    @Test
    void mirroredSidesStaySymmetric() {
        var left = MagneticWeaponBladeMotion.build(ORIGIN, TARGET, IDLE, SWEEP, null, null);
        var right = MagneticWeaponBladeMotion.build(
                ORIGIN, TARGET, IDLE, new Sweep(-1.0f, SWEEP.diagonal(), SWEEP.swing()), null, null);
        assertNotNull(left);
        assertNotNull(right);

        var sampledLeft = MagneticWeaponBladeMotion.sample(left, SWEEP, left.impactMark() * 0.5);
        var sampledRight = MagneticWeaponBladeMotion.sample(
                right, new Sweep(-1.0f, SWEEP.diagonal(), SWEEP.swing()), right.impactMark() * 0.5);
        assertTrue(Math.abs(sampledLeft.roll() - sampledRight.roll()) > 1.0E-3f);
        assertTrue(Math.abs(sampledLeft.yaw() - sampledRight.yaw()) > 1.0E-3f);
    }

    @Test
    void phaseBoundariesStayContinuous() {
        assertEquals(
                MagneticWeaponBladeMotion.rollAt(Phase.PREP, 1.0, SWEEP),
                MagneticWeaponBladeMotion.rollAt(Phase.STRIKE, 0.0, SWEEP), 1.0E-6f);
        assertEquals(
                MagneticWeaponBladeMotion.rollAt(Phase.STRIKE, 1.0, SWEEP),
                MagneticWeaponBladeMotion.rollAt(Phase.RETURN, 0.0, SWEEP), 1.0E-6f);
        assertEquals(MagneticWeaponBladeMotion.IDLE_ROLL,
                MagneticWeaponBladeMotion.rollAt(Phase.RETURN, 1.0, SWEEP), 1.0E-6f);
        assertEquals(MagneticWeaponBladeMotion.IDLE_ROLL,
                MagneticWeaponBladeMotion.rollAt(Phase.PREP, 0.5, SWEEP), 1.0E-6f);
        assertVecEquals(
                MagneticWeaponBladeMotion.facingDirection(TARGET, Phase.PREP, 1.0, SWEEP),
                MagneticWeaponBladeMotion.facingDirection(TARGET, Phase.STRIKE, 0.0, SWEEP), 1.0E-6);
        assertVecEquals(
                MagneticWeaponBladeMotion.facingDirection(TARGET, Phase.STRIKE, 1.0, SWEEP),
                MagneticWeaponBladeMotion.facingDirection(TARGET, Phase.RETURN, 0.0, SWEEP), 1.0E-6);
    }
}
