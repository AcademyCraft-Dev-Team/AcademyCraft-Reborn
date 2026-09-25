package org.academy.internal.common.ability.teleport;

import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class SpacialExcisionMath {
    public static final double MIN_DISPLACEMENT_SQUARED = 1.0e-6;
    public static final int TRANSITION_TICKS = 4;

    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_X = new Vec3(1.0, 0.0, 0.0);

    private SpacialExcisionMath() {
    }

    public static Optional<PlaneBasis> planeBasis(Vec3 start, Vec3 end, float preTeleportYaw) {
        if (!isFinite(start) || !isFinite(end) || !Float.isFinite(preTeleportYaw)) {
            return Optional.empty();
        }
        var delta = end.subtract(start);
        var lengthSquared = delta.lengthSqr();
        if (!Double.isFinite(lengthSquared) || lengthSquared <= MIN_DISPLACEMENT_SQUARED) {
            return Optional.empty();
        }

        var tangent = delta.normalize();
        var upProjection = WORLD_UP.subtract(tangent.scale(WORLD_UP.dot(tangent)));
        Vec3 planeUp;
        if (upProjection.lengthSqr() > 1.0e-8) {
            planeUp = upProjection.normalize();
        } else {
            var yaw = Math.toRadians(preTeleportYaw);
            var horizontalRight = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
            var rightProjection = horizontalRight.subtract(tangent.scale(horizontalRight.dot(tangent)));
            planeUp = rightProjection.lengthSqr() > 1.0e-8
                    ? rightProjection.normalize()
                    : WORLD_X.subtract(tangent.scale(WORLD_X.dot(tangent))).normalize();
        }

        var planeNormal = tangent.cross(planeUp);
        if (!isFinite(tangent) || !isFinite(planeUp)
                || !isFinite(planeNormal) || planeNormal.lengthSqr() <= 1.0e-8) {
            return Optional.empty();
        }
        return Optional.of(new PlaneBasis(tangent, planeUp, planeNormal.normalize()));
    }

    public static float transitionProgress(long now, long createdTick, long endTick) {
        return transitionProgress((double) now, createdTick, endTick);
    }

    public static float transitionProgress(double now, long createdTick, long endTick) {
        return transitionProgress(now, (double) createdTick, (double) endTick);
    }

    public static float transitionProgress(double now, double createdTick, double endTick) {
        if (endTick <= createdTick || now <= createdTick || now >= endTick) {
            return now > createdTick && now < endTick ? 1.0f : 0.0f;
        }
        var opening = Math.min(1.0, (now - createdTick) / (double) TRANSITION_TICKS);
        var closing = Math.min(1.0, (endTick - now) / (double) TRANSITION_TICKS);
        return (float) Math.max(0.0, Math.min(1.0, Math.min(opening, closing)));
    }

    public static boolean isFinite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    public static float frontClipInterpolation(float fromW, float toW, float minimumW) {
        if (!Float.isFinite(fromW) || !Float.isFinite(toW)
                || !Float.isFinite(minimumW) || minimumW < 0.0f) {
            return Float.NaN;
        }
        var denominator = toW - fromW;
        if (!Float.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12f) {
            return Float.NaN;
        }
        var fraction = (minimumW - fromW) / denominator;
        return Float.isFinite(fraction)
                ? Math.clamp(fraction, 0.0f, 1.0f)
                : Float.NaN;
    }

    public record PlaneBasis(Vec3 tangent, Vec3 planeUp, Vec3 planeNormal) {
    }
}
