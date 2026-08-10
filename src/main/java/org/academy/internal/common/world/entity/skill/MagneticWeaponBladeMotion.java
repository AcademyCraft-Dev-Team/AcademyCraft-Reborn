package org.academy.internal.common.world.entity.skill;

import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

public final class MagneticWeaponBladeMotion {
    public static final int PREP_END_TICK = 2;
    public static final int IMPACT_TICK = 5;
    public static final int ATTACK_END_TICK = 10;

    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private MagneticWeaponBladeMotion() {
    }

    public static Phase phaseAt(int attackTick) {
        if (attackTick <= 0) return Phase.IDLE;
        if (attackTick <= PREP_END_TICK) return Phase.PREP;
        if (attackTick <= IMPACT_TICK) return Phase.STRIKE;
        if (attackTick <= ATTACK_END_TICK) return Phase.RETURN;
        return Phase.IDLE;
    }

    public static boolean crossesImpact(int previousTick, int currentTick) {
        return previousTick < IMPACT_TICK && currentTick >= IMPACT_TICK;
    }

    public static Vec3 idlePosition(Vec3 ownerPosition, float ownerYaw, long age) {
        var forward = Vec3.directionFromRotation(0.0f, ownerYaw).normalize();
        var right = new Vec3(-forward.z, 0.0, forward.x);
        var bob = Mth.sin(age * 0.16) * 0.06;
        return ownerPosition
                .subtract(forward.scale(0.72))
                .add(right.scale(0.42))
                .add(0.0, 1.35 + bob, 0.0);
    }

    public static Motion sample(Vec3 attackOrigin, Vec3 targetPosition, Vec3 idlePosition,
                                int attackTick, int attackSequence) {
        var phase = phaseAt(attackTick);
        var strikeDirection = normalizedOr(targetPosition.subtract(attackOrigin), new Vec3(0.0, 0.0, 1.0));
        var side = normalizedOr(strikeDirection.cross(UP), new Vec3(1.0, 0.0, 0.0));
        var sideSign = (attackSequence & 1) == 0 ? 1.0 : -1.0;
        var distance = attackOrigin.distanceTo(targetPosition);
        var curve = Mth.clamp(distance * 0.18, 0.35, 1.2);
        var windup = attackOrigin.subtract(strikeDirection.scale(0.18)).add(0.0, 0.08, 0.0);

        return switch (phase) {
            case IDLE -> new Motion(idlePosition, idleTangent(), 100.0f);
            case PREP -> {
                var progress = smoothstep(attackTick / (double) PREP_END_TICK);
                yield new Motion(attackOrigin.lerp(windup, progress), strikeDirection, 100.0f);
            }
            case STRIKE -> {
                var raw = (attackTick - PREP_END_TICK)
                        / (double) (IMPACT_TICK - PREP_END_TICK);
                var progress = Mth.clamp(raw * raw, 0.0, 1.0);
                var control1 = windup
                        .add(strikeDirection.scale(distance * 0.25))
                        .add(side.scale(curve * sideSign));
                var control2 = targetPosition
                        .subtract(strikeDirection.scale(distance * 0.15))
                        .add(side.scale(curve * 0.35 * sideSign));
                var position = cubic(windup, control1, control2, targetPosition, progress);
                var tangent = cubicDerivative(windup, control1, control2, targetPosition, progress);
                yield new Motion(position, normalizedOr(tangent, strikeDirection), rollAt(attackTick, attackSequence));
            }
            case RETURN -> {
                var raw = (attackTick - IMPACT_TICK)
                        / (double) (ATTACK_END_TICK - IMPACT_TICK);
                var progress = Mth.clamp(raw * (2.0 - raw), 0.0, 1.0);
                var returnDirection = normalizedOr(idlePosition.subtract(targetPosition), strikeDirection.reverse());
                var returnSide = side.scale(-sideSign);
                var control1 = targetPosition.add(returnSide.scale(curve)).add(0.0, 0.4, 0.0);
                var control2 = idlePosition.add(returnSide.scale(curve * 0.35)).add(0.0, 0.2, 0.0);
                var position = cubic(targetPosition, control1, control2, idlePosition, progress);
                var tangent = cubicDerivative(targetPosition, control1, control2, idlePosition, progress);
                yield new Motion(position, normalizedOr(tangent, returnDirection), rollAt(attackTick, attackSequence));
            }
        };
    }

    public static float rollAt(int attackTick, int attackSequence) {
        var sideSign = (attackSequence & 1) == 0 ? 1.0 : -1.0;
        return switch (phaseAt(attackTick)) {
            case IDLE, PREP -> 100.0f;
            case STRIKE -> {
                var raw = (attackTick - PREP_END_TICK)
                        / (double) (IMPACT_TICK - PREP_END_TICK);
                yield (float) (sideSign * 18.0 * Mth.sin(Mth.PI * raw));
            }
            case RETURN -> {
                var raw = (attackTick - IMPACT_TICK)
                        / (double) (ATTACK_END_TICK - IMPACT_TICK);
                yield (float) (-sideSign * 12.0 * Mth.sin(Mth.PI * raw));
            }
        };
    }

    public static Vec3 cubic(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double progress) {
        var t = Mth.clamp(progress, 0.0, 1.0);
        var inverse = 1.0 - t;
        return p0.scale(inverse * inverse * inverse)
                .add(p1.scale(3.0 * inverse * inverse * t))
                .add(p2.scale(3.0 * inverse * t * t))
                .add(p3.scale(t * t * t));
    }

    private static Vec3 cubicDerivative(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double progress) {
        var t = Mth.clamp(progress, 0.0, 1.0);
        var inverse = 1.0 - t;
        return p1.subtract(p0).scale(3.0 * inverse * inverse)
                .add(p2.subtract(p1).scale(6.0 * inverse * t))
                .add(p3.subtract(p2).scale(3.0 * t * t));
    }

    private static Vec3 idleTangent() {
        return Vec3.directionFromRotation(75.0f, -90.0f).normalize();
    }

    private static Vec3 normalizedOr(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() > 1.0E-8 ? vector.normalize() : fallback.normalize();
    }

    private static double smoothstep(double value) {
        var t = Mth.clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public enum Phase {
        IDLE,
        PREP,
        STRIKE,
        RETURN
    }

    public record Motion(Vec3 position, Vec3 tangent, float roll) {
    }
}
