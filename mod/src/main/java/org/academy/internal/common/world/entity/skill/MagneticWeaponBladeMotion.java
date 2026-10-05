package org.academy.internal.common.world.entity.skill;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class MagneticWeaponBladeMotion {
    public static final int MIN_STRIKE_TICKS = 6;
    public static final int MIN_RETURN_TICKS = 6;
    public static final int MAX_STRIKE_TICKS = 24;
    public static final int MAX_RETURN_TICKS = 30;
    public static final double STRIKE_TICKS_PER_BLOCK = 0.6;
    public static final double RETURN_TICKS_PER_BLOCK = 0.6;
    public static final float IDLE_ROLL = 100.0f;
    public static final float MAX_SWEEP_DIAGONAL = 35.0f;
    public static final float MIN_SWEEP_SWING = 70.0f;
    public static final float MAX_SWEEP_SWING = 160.0f;
    public static final float RETURN_GRACE_TICKS = 1.5f;
    public static final int PREP_TICKS = 3;

    private static final double WINDUP_RISE = 0.4;
    private static final double WINDUP_OUTWARD = 0.3;
    private static final double CONTROL_OUTWARD = 1.15;
    private static final double CONTROL_RISE = 0.9;
    private static final double RETURN_LIFT = 1.3;
    private static final double RETURN_LIFT_OUTWARD = 0.9;
    private static final double RETURN_LIFT_BACKSTEP = 0.6;
    private static final double STRIKE_EASE = 1.7;
    private static final double RETURN_EASE = 1.4;
    private static final double OVERSHOOT_RETURN_FRACTION = 0.25;
    private static final double OVERSHOOT_EXTRA_SWING = 0.35;
    private static final double OVERSHOOT_REACH_MIN = 0.3;
    private static final double OVERSHOOT_REACH = 0.15;
    private static final double PREP_SWING = 0.35;
    private static final double IMPACT_SWING = -0.55;
    private static final double OVERSHOOT_SWING = -0.85;
    private static final float ROLL_SWING = 40.0f;
    private static final float ROLL_TWIRL = 360.0f;
    private static final double EPSILON = 1.0E-7;

    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 IDLE_TANGENT = Vec3.directionFromRotation(75.0f, -90.0f).normalize();

    private MagneticWeaponBladeMotion() {
    }

    public record Timing(int strikeTicks, int returnTicks) {
        public int totalTicks() {
            return strikeTicks + returnTicks;
        }

        public static Timing forDistance(double distance) {
            var strike = Mth.clamp(
                    (int) Math.ceil(distance * STRIKE_TICKS_PER_BLOCK), MIN_STRIKE_TICKS, MAX_STRIKE_TICKS);
            var returning = Mth.clamp(
                    (int) Math.ceil(distance * RETURN_TICKS_PER_BLOCK), MIN_RETURN_TICKS, MAX_RETURN_TICKS);
            return new Timing(strike, returning);
        }
    }

    public record Sweep(float side, float diagonal, float swing) {
        public static final Sweep NONE = new Sweep(1.0f, 0.0f, 0.0f);
    }

    public record BladePath(List<Vec3> knots, List<Double> marks, int impactIndex) {
        public Vec3 origin() {
            return knots.getFirst();
        }

        public Vec3 impactPosition() {
            return knots.get(impactIndex);
        }

        public double impactMark() {
            return marks.get(impactIndex);
        }

        public double endMark() {
            return marks.get(marks.size() - 1);
        }

        public void moveImpact(Vec3 position) {
            knots.set(impactIndex, position);
        }

        public void moveReturnEnd(Vec3 position) {
            knots.set(knots.size() - 1, position);
        }

        public void refreshMarks() {
            marks.clear();
            marks.addAll(buildMarks(knots));
        }
    }

    public static double progressAt(double elapsedTicks, Timing timing, BladePath path) {
        var prepMark = path.marks().get(1);
        var impactMark = path.impactMark();
        var endMark = path.endMark();
        if (elapsedTicks <= 0.0) return 0.0;
        if (elapsedTicks <= PREP_TICKS) {
            return prepMark * smootherstep(elapsedTicks / PREP_TICKS);
        }
        if (elapsedTicks <= timing.strikeTicks()) {
            var span = timing.strikeTicks() - PREP_TICKS;
            var fraction = span > 0 ? (elapsedTicks - PREP_TICKS) / span : 1.0;
            return Mth.lerp(easeIn(fraction, STRIKE_EASE), prepMark, impactMark);
        }
        var after = elapsedTicks - timing.strikeTicks();
        if (after <= timing.returnTicks()) {
            return Mth.lerp(easeOut(after / timing.returnTicks(), RETURN_EASE), impactMark, endMark);
        }
        return endMark;
    }

    public static boolean crossesImpact(double previousProgress, double currentProgress, double impactMark) {
        return previousProgress < impactMark && currentProgress >= impactMark;
    }

    @Nullable
    public static BladePath build(Vec3 attackOrigin, Vec3 targetPosition, Vec3 idlePosition, Sweep sweep,
                                  @Nullable List<Vec3> strikeRoute, @Nullable List<Vec3> returnRoute) {
        if (targetPosition.distanceToSqr(attackOrigin) < 1.0E-8) return null;
        var direction = normalizedOr(targetPosition.subtract(attackOrigin), new Vec3(0.0, 0.0, 1.0));
        var side = normalizedOr(direction.cross(UP), new Vec3(1.0, 0.0, 0.0));
        var windup = attackOrigin
                .add(0.0, WINDUP_RISE, 0.0)
                .add(side.scale(WINDUP_OUTWARD * sweep.side()));
        var overshoot = overshootPoint(attackOrigin, targetPosition, direction, sweep);

        var strikePoints = new ArrayList<Vec3>();
        strikePoints.add(attackOrigin);
        strikePoints.add(windup);
        if (routed(strikeRoute)) {
            for (var index = 1; index < strikeRoute.size() - 1; index++) {
                strikePoints.add(strikeRoute.get(index));
            }
        } else {
            strikePoints.add(approachControl(windup, targetPosition, direction, sweep));
        }
        strikePoints.add(targetPosition);

        var returnPoints = new ArrayList<Vec3>();
        returnPoints.add(targetPosition);
        if (routed(returnRoute)) {
            for (var index = 1; index < returnRoute.size() - 1; index++) {
                returnPoints.add(returnRoute.get(index));
            }
        } else {
            returnPoints.add(targetPosition
                    .add(0.0, RETURN_LIFT, 0.0)
                    .add(side.scale(RETURN_LIFT_OUTWARD * sweep.side()))
                    .add(direction.scale(-RETURN_LIFT_BACKSTEP)));
        }
        returnPoints.add(idlePosition);

        var knots = new ArrayList<Vec3>(strikePoints.size() + returnPoints.size());
        knots.addAll(strikePoints);
        knots.add(overshoot);
        for (var index = 1; index < returnPoints.size(); index++) {
            knots.add(returnPoints.get(index));
        }
        if (knots.size() < 3) return null;

        var impactIndex = strikePoints.size() - 1;
        return new BladePath(knots, buildMarks(knots), impactIndex);
    }

    public static Vec3 position(BladePath path, double progress) {
        return point(path, Mth.clamp(progress, 0.0, path.endMark()));
    }

    public static Vec3 tangent(BladePath path, double progress) {
        var clamped = Mth.clamp(progress, 0.0, path.endMark());
        if (clamped <= 0.0) return IDLE_TANGENT;
        var knots = path.knots();
        var marks = path.marks();
        var last = marks.size() - 1;
        var index = markIndex(marks, clamped);
        var span = marks.get(index + 1) - marks.get(index);
        var fraction = span > EPSILON ? (clamped - marks.get(index)) / span : 0.0;
        return catmullRomDerivative(
                knots.get(Math.max(index - 1, 0)),
                knots.get(index),
                knots.get(index + 1),
                knots.get(Math.min(index + 2, last)),
                fraction
        );
    }

    public static BladeTransform sample(BladePath path, Sweep sweep, double progress) {
        var clamped = Mth.clamp(progress, 0.0, path.endMark());
        var phase = phaseAt(path, clamped);
        var fraction = phaseFractionAt(path, clamped, phase);
        var roll = rollAt(phase, fraction, sweep);
        var base = normalizedOr(path.impactPosition().subtract(path.origin()), new Vec3(0.0, 0.0, 1.0));
        var cutAxis = cutAxis(base, sweep);
        var facing = rotateAboutAxis(tangent(path, clamped), cutAxis,
                Math.toRadians(sweep.swing() * sweep.side() * swingEnvelope(phase, fraction)));
        var transform = transform(point(path, clamped), facing, roll);
        return phase == Phase.PREP ? blendToStrike(transform, fraction) : transform;
    }

    public static BladeTransform idleTransform(Vec3 idlePosition) {
        return new BladeTransform(
                idlePosition,
                BladeGeometry.yawFromDirection(IDLE_TANGENT),
                BladeGeometry.pitchFromDirection(IDLE_TANGENT),
                IDLE_ROLL
        );
    }

    public static Phase phaseAt(BladePath path, double progress) {
        if (progress < path.marks().get(1)) return Phase.PREP;
        if (progress < path.impactMark()) return Phase.STRIKE;
        return Phase.RETURN;
    }

    public static double phaseFractionAt(BladePath path, double progress, Phase phase) {
        var marks = path.marks();
        return switch (phase) {
            case PREP -> marks.get(1) < EPSILON ? 0.0 : Mth.clamp(progress / marks.get(1), 0.0, 1.0);
            case STRIKE -> {
                var span = path.impactMark() - marks.get(1);
                yield span < EPSILON ? 0.0 : Mth.clamp((progress - marks.get(1)) / span, 0.0, 1.0);
            }
            case RETURN -> {
                var span = path.endMark() - path.impactMark();
                yield span < EPSILON ? 0.0 : Mth.clamp((progress - path.impactMark()) / span, 0.0, 1.0);
            }
        };
    }

    public static float rollAt(Phase phase, double fraction, Sweep sweep) {
        var progress = Mth.clamp(fraction, 0.0, 1.0);
        return switch (phase) {
            case PREP -> IDLE_ROLL;
            case STRIKE -> (float) (IDLE_ROLL + (sweep.diagonal() + sweep.side() * ROLL_SWING) * bump(progress));
            case RETURN -> (float) (IDLE_ROLL + (sweep.diagonal() + sweep.side() * ROLL_TWIRL) * bump(progress));
        };
    }

    public static Vec3 facingDirection(Vec3 base, Phase phase, double fraction, Sweep sweep) {
        var cut = cutAxis(base, sweep);
        var radians = Math.toRadians(sweep.swing() * sweep.side() * swingEnvelope(phase, fraction));
        return rotateAboutAxis(base, cut, radians);
    }

    public static Vec3 idlePosition(Vec3 ownerPosition, double ownerYaw, double age) {
        var forward = Vec3.directionFromRotation(0.0f, (float) ownerYaw).normalize();
        var right = new Vec3(-forward.z, 0.0, forward.x);
        var bob = Mth.sin((float) (age * 0.16)) * 0.06;
        return ownerPosition
                .subtract(forward.scale(0.72))
                .add(right.scale(0.42))
                .add(0.0, 1.35 + bob, 0.0);
    }

    public static byte @Nullable [] packPath(@Nullable BladePath path) {
        if (path == null) return null;
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(path.knots().size());
            for (var knot : path.knots()) {
                buffer.writeDouble(knot.x);
                buffer.writeDouble(knot.y);
                buffer.writeDouble(knot.z);
            }
            buffer.writeVarInt(path.impactIndex());
            var bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    @Nullable
    public static BladePath unpackPath(byte @Nullable [] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            var count = buffer.readVarInt();
            if (count < 3) return null;
            var knots = new ArrayList<Vec3>(count);
            for (var index = 0; index < count; index++) {
                knots.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
            }
            var impactIndex = buffer.readVarInt();
            if (impactIndex <= 0 || impactIndex >= count - 1) return null;
            return new BladePath(knots, buildMarks(knots), impactIndex);
        } catch (Exception exception) {
            return null;
        } finally {
            buffer.release();
        }
    }

    private static List<Double> buildMarks(List<Vec3> knots) {
        var count = knots.size();
        var cumulative = new double[count];
        var total = 0.0;
        for (var index = 1; index < count; index++) {
            total += knots.get(index - 1).distanceTo(knots.get(index));
            cumulative[index] = total;
        }
        var marks = new ArrayList<Double>(count);
        for (var index = 0; index < count; index++) {
            var value = total > EPSILON ? cumulative[index] / total : index / (double) (count - 1);
            marks.add(value);
        }
        for (var index = 1; index < count; index++) {
            if (marks.get(index) <= marks.get(index - 1)) {
                marks.set(index, marks.get(index - 1) + EPSILON);
            }
        }
        var last = marks.get(count - 1);
        if (last > EPSILON && Math.abs(last - 1.0) > EPSILON) {
            for (var index = 0; index < count; index++) marks.set(index, marks.get(index) / last);
        }
        marks.set(count - 1, 1.0);
        return marks;
    }

    private static Vec3 point(BladePath path, double progress) {
        var knots = path.knots();
        var marks = path.marks();
        if (progress <= 0.0) return knots.getFirst();
        var last = marks.size() - 1;
        var index = markIndex(marks, progress);
        var span = marks.get(index + 1) - marks.get(index);
        var fraction = span > EPSILON ? (progress - marks.get(index)) / span : 0.0;
        return catmullRom(
                knots.get(Math.max(index - 1, 0)),
                knots.get(index),
                knots.get(index + 1),
                knots.get(Math.min(index + 2, last)),
                fraction
        );
    }

    private static BladeTransform blendToStrike(BladeTransform sampled, double fraction) {
        var approach = smootherstep(fraction);
        if (approach >= 1.0) return sampled;
        var direction = BladeGeometry.rotateTowards(
                IDLE_TANGENT, BladeGeometry.direction(sampled.yaw(), sampled.pitch()), approach);
        return new BladeTransform(sampled.position(),
                BladeGeometry.yawFromDirection(direction),
                BladeGeometry.pitchFromDirection(direction),
                sampled.roll());
    }

    private static BladeTransform transform(Vec3 position, Vec3 tangent, float roll) {
        if (tangent.lengthSqr() < 1.0E-8) {
            return new BladeTransform(position, 0.0f, 0.0f, roll);
        }
        var direction = tangent.normalize();
        return new BladeTransform(
                position,
                BladeGeometry.yawFromDirection(direction),
                BladeGeometry.pitchFromDirection(direction),
                roll
        );
    }

    private static Vec3 overshootPoint(Vec3 attackOrigin, Vec3 targetPosition, Vec3 direction, Sweep sweep) {
        var cutAxis = cutAxis(direction, sweep);
        var swept = rotateAboutAxis(direction, cutAxis,
                Math.toRadians(-OVERSHOOT_EXTRA_SWING * sweep.swing() * sweep.side()));
        var reach = Math.max(OVERSHOOT_REACH_MIN,
                Math.sqrt(attackOrigin.distanceToSqr(targetPosition)) * OVERSHOOT_REACH);
        return targetPosition.add(swept.scale(reach));
    }

    private static Vec3 approachControl(Vec3 windup, Vec3 targetPosition, Vec3 direction, Sweep sweep) {
        var side = direction.cross(UP).normalize();
        var hand = side.scale(sweep.side() >= 0.0f ? 1.0 : -1.0);
        return windup.lerp(targetPosition, 0.45)
                .add(hand.scale(CONTROL_OUTWARD))
                .add(0.0, CONTROL_RISE, 0.0);
    }

    private static Vec3 cutAxis(Vec3 base, Sweep sweep) {
        var side = normalizedOr(base.cross(UP), new Vec3(1.0, 0.0, 0.0));
        return rotateAboutAxis(side, base, Math.toRadians(sweep.diagonal()));
    }

    private static double swingEnvelope(Phase phase, double fraction) {
        var progress = Mth.clamp(fraction, 0.0, 1.0);
        return switch (phase) {
            case PREP -> PREP_SWING * smootherstep(progress);
            case STRIKE -> PREP_SWING + (IMPACT_SWING - PREP_SWING) * smootherstep(progress);
            case RETURN -> {
                if (progress <= OVERSHOOT_RETURN_FRACTION) {
                    yield IMPACT_SWING + (OVERSHOOT_SWING - IMPACT_SWING)
                            * smootherstep(progress / OVERSHOOT_RETURN_FRACTION);
                }
                yield OVERSHOOT_SWING
                        * (1.0 - smootherstep(segment(progress, OVERSHOOT_RETURN_FRACTION, 1.0)));
            }
        };
    }

    private static double bump(double t) {
        var value = Mth.clamp(t, 0.0, 1.0);
        var product = value * (1.0 - value);
        return 16.0 * product * product;
    }

    private static double segment(double value, double from, double to) {
        return Mth.clamp((value - from) / (to - from), 0.0, 1.0);
    }

    private static double smootherstep(double t) {
        var value = Mth.clamp(t, 0.0, 1.0);
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double easeIn(double t, double power) {
        return Math.pow(Mth.clamp(t, 0.0, 1.0), power);
    }

    private static double easeOut(double t, double power) {
        return 1.0 - Math.pow(1.0 - Mth.clamp(t, 0.0, 1.0), power);
    }

    private static Vec3 rotateAboutAxis(Vec3 vector, Vec3 axis, double radians) {
        var unit = axis.normalize();
        var cosine = Math.cos(radians);
        var sine = Math.sin(radians);
        return vector.scale(cosine)
                .add(unit.cross(vector).scale(sine))
                .add(unit.scale(unit.dot(vector) * (1.0 - cosine)));
    }

    private static boolean routed(@Nullable List<Vec3> route) {
        return route != null && route.size() >= 3;
    }

    private static Vec3 normalizedOr(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() > 1.0E-8 ? vector.normalize() : fallback.normalize();
    }

    private static int markIndex(List<Double> marks, double value) {
        var low = 0;
        var high = marks.size() - 1;
        while (low < high - 1) {
            var middle = (low + high) >> 1;
            if (marks.get(middle) <= value) low = middle;
            else high = middle;
        }
        return low;
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        var t2 = t * t;
        var t3 = t2 * t;
        return p1.scale(2.0)
                .add(p2.subtract(p0).scale(t))
                .add(p0.scale(2.0).subtract(p1.scale(5.0)).add(p2.scale(4.0)).subtract(p3).scale(t2))
                .add(p0.reverse().add(p1.scale(3.0)).subtract(p2.scale(3.0)).add(p3).scale(t3))
                .scale(0.5);
    }

    private static Vec3 catmullRomDerivative(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        var t2 = t * t;
        return p2.subtract(p0)
                .add(p0.scale(2.0).subtract(p1.scale(5.0)).add(p2.scale(4.0)).subtract(p3).scale(2.0 * t))
                .add(p0.reverse().add(p1.scale(3.0)).subtract(p2.scale(3.0)).add(p3).scale(3.0 * t2))
                .scale(0.5);
    }

    public enum Phase {
        PREP,
        STRIKE,
        RETURN
    }
}
