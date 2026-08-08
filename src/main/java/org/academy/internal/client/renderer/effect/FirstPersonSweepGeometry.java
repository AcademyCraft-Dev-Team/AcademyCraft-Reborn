package org.academy.internal.client.renderer.effect;

final class FirstPersonSweepGeometry {
    static final int IRON_SAND_PARTICLES = 24;
    private static final float WING_LENGTH = 3.5f;

    private FirstPersonSweepGeometry() {
    }

    static WingProjection wingProjection(boolean leftWing, float progress) {
        var side = leftWing ? -1.0f : 1.0f;
        var clampedProgress = Math.clamp(progress, 0.0f, 1.0f);
        var eased = smoothstep(clampedProgress);
        var sweepDegrees = -side * (55.0f - eased * 110.0f);
        var alpha = (float) Math.sin(Math.PI * clampedProgress);
        var scale = 0.32f + alpha * 0.04f;
        return new WingProjection(
                side * 0.46f, -0.42f, -0.78f,
                sweepDegrees, -52.0f, scale, alpha
        );
    }

    static Point ironSandPosition(float handSide, float progress, int particleIndex) {
        var clampedProgress = Math.clamp(progress, 0.0f, 1.0f);
        var radialProgress = particleIndex / (float) (IRON_SAND_PARTICLES - 1);
        var eased = smoothstep(clampedProgress);
        var sweepDegrees = handSide * (60.0f - eased * 120.0f);
        var trailingDegrees = handSide * (1.0f - radialProgress) * 24.0f;
        var angle = Math.toRadians(sweepDegrees + trailingDegrees);
        var radius = 0.55f + radialProgress * 2.20f;
        var x = (float) Math.sin(angle) * radius;
        var y = -0.48f + radialProgress * 0.45f
                + (float) Math.sin(particleIndex * 0.72f) * 0.05f
                + (float) Math.sin(Math.PI * clampedProgress) * 0.12f;
        var z = -0.55f - radialProgress * 3.45f;
        return new Point(x, y, z);
    }

    static float ironSandScale(int particleIndex) {
        var radialProgress = particleIndex / (float) (IRON_SAND_PARTICLES - 1);
        return 0.08f + radialProgress * 0.18f;
    }

    static float ironSandAlpha(float progress, int particleIndex) {
        var clampedProgress = Math.clamp(progress, 0.0f, 1.0f);
        var radialProgress = particleIndex / (float) (IRON_SAND_PARTICLES - 1);
        return (0.75f - radialProgress * 0.20f)
                * (float) Math.sin(Math.PI * clampedProgress);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0f - 2.0f * value);
    }

    record Point(float x, float y, float z) {
    }

    record WingProjection(float rootX, float rootY, float rootZ,
                          float sweepDegrees, float tiltDegrees,
                          float scale, float alpha) {
        Point centerline(float normalizedLength) {
            var length = WING_LENGTH * scale * Math.clamp(normalizedLength, 0.0f, 1.0f);
            var sweep = Math.toRadians(sweepDegrees);
            var tilt = Math.toRadians(tiltDegrees);
            var projectedLength = (float) Math.cos(tilt) * length;
            return new Point(
                    rootX - (float) Math.sin(sweep) * projectedLength,
                    rootY + (float) Math.cos(sweep) * projectedLength,
                    rootZ + (float) Math.sin(tilt) * length
            );
        }
    }
}
