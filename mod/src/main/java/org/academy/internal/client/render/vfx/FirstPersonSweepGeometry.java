package org.academy.internal.client.render.vfx;

import net.minecraft.util.Mth;

public final class FirstPersonSweepGeometry {
    public static final int IRON_SAND_PARTICLES = 24;
    private static final float WING_LENGTH = 3.5f;

    private FirstPersonSweepGeometry() {
    }

    public static WingProjection wingProjection(boolean leftWing, float progress) {
        var side = leftWing ? -1.0f : 1.0f;
        var clampedProgress = Mth.clamp(progress, 0.0f, 1.0f);
        var eased = smoothstep(clampedProgress);
        var sweepDegrees = -side * (55.0f - eased * 110.0f);
        var alpha = Mth.sin(Mth.PI * clampedProgress);
        var scale = 0.32f + alpha * 0.04f;
        return new WingProjection(
                side * 0.46f, -0.42f, -0.78f,
                sweepDegrees, -52.0f, scale, alpha
        );
    }

    public static Point ironSandPosition(float handSide, float progress, int particleIndex) {
        var clampedProgress = Mth.clamp(progress, 0.0f, 1.0f);
        var radialProgress = particleIndex / (float) (IRON_SAND_PARTICLES - 1);
        var eased = smoothstep(clampedProgress);
        var sweepDegrees = handSide * (60.0f - eased * 120.0f);
        var trailingDegrees = handSide * (1.0f - radialProgress) * 24.0f;
        var angle = (sweepDegrees + trailingDegrees) * Mth.DEG_TO_RAD;
        var radius = 0.55f + radialProgress * 2.20f;
        var x = Mth.sin(angle) * radius;
        var y = -0.48f + radialProgress * 0.45f
                + Mth.sin(particleIndex * 0.72f) * 0.05f
                + Mth.sin(Mth.PI * clampedProgress) * 0.12f;
        var z = -0.55f - radialProgress * 3.45f;
        return new Point(x, y, z);
    }

    public static float ironSandScale(int particleIndex) {
        var radialProgress = particleIndex / (float) (IRON_SAND_PARTICLES - 1);
        return 0.08f + radialProgress * 0.18f;
    }

    public static float ironSandAlpha(float progress, int particleIndex) {
        var clampedProgress = Mth.clamp(progress, 0.0f, 1.0f);
        var radialProgress = particleIndex / (float) (IRON_SAND_PARTICLES - 1);
        return (0.75f - radialProgress * 0.20f)
                * Mth.sin(Mth.PI * clampedProgress);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0f - 2.0f * value);
    }

    public record Point(float x, float y, float z) {
    }

    public record WingProjection(float rootX, float rootY, float rootZ,
                                 float sweepDegrees, float tiltDegrees,
                                 float scale, float alpha) {
        Point centerline(float normalizedLength) {
            var length = WING_LENGTH * scale * Mth.clamp(normalizedLength, 0.0f, 1.0f);
            var sweep = (sweepDegrees) * Mth.DEG_TO_RAD;
            var tilt = (tiltDegrees) * Mth.DEG_TO_RAD;
            var projectedLength = Mth.cos(tilt) * length;
            return new Point(
                    rootX - Mth.sin(sweep) * projectedLength,
                    rootY + Mth.cos(sweep) * projectedLength,
                    rootZ + Mth.sin(tilt) * length
            );
        }
    }
}
