package org.academy.internal.client.render.vfx;

final class WingTransitionAnimation {
    static final int DURATION_TICKS = 28;

    private WingTransitionAnimation() {
    }

    static boolean isActive(double elapsedTicks) {
        return elapsedTicks >= 0.0 && elapsedTicks < DURATION_TICKS;
    }

    static Projection sample(double elapsedTicks) {
        var progress = clamp((float) (elapsedTicks / DURATION_TICKS));
        return new Projection(blackWing(progress), whiteWing(progress), ascension(progress));
    }

    private static Pose blackWing(float progress) {
        if (progress < 0.18f) {
            var phase = smooth(segment(progress, 0.0f, 0.18f));
            return new Pose(
                    lerp(1.0f, 1.08f, phase),
                    lerp(1.0f, 1.04f, phase),
                    30.0f,
                    30.0f
            );
        }
        if (progress < 0.42f) {
            var phase = cubicIn(segment(progress, 0.18f, 0.42f));
            return new Pose(
                    lerp(1.08f, 0.0f, phase),
                    lerp(1.04f, 0.52f, phase),
                    lerp(30.0f, 6.0f, phase),
                    lerp(30.0f, 72.0f, phase)
            );
        }
        return Pose.HIDDEN;
    }

    private static Pose whiteWing(float progress) {
        if (progress < 0.40f) return Pose.HIDDEN;
        if (progress < 0.70f) {
            var phase = cubicOut(segment(progress, 0.40f, 0.70f));
            return new Pose(
                    lerp(0.02f, 1.18f, phase),
                    lerp(0.20f, 1.06f, phase),
                    lerp(6.0f, 35.0f, phase),
                    lerp(72.0f, 24.0f, phase)
            );
        }
        var phase = smooth(segment(progress, 0.70f, 1.0f));
        return new Pose(
                lerp(1.18f, 1.0f, phase),
                lerp(1.06f, 1.0f, phase),
                lerp(35.0f, 30.0f, phase),
                lerp(24.0f, 30.0f, phase)
        );
    }

    private static Pose ascension(float progress) {
        if (progress < 0.34f || progress >= 0.76f) return Pose.HIDDEN;
        if (progress < 0.53f) {
            var phase = cubicOut(segment(progress, 0.34f, 0.53f));
            return new Pose(
                    lerp(0.04f, 1.34f, phase),
                    lerp(0.28f, 1.22f, phase),
                    lerp(2.0f, 24.0f, phase),
                    lerp(80.0f, 38.0f, phase)
            );
        }
        var phase = smooth(segment(progress, 0.53f, 0.76f));
        return new Pose(
                lerp(1.34f, 0.0f, phase),
                lerp(1.22f, 0.48f, phase),
                lerp(24.0f, 30.0f, phase),
                lerp(38.0f, 30.0f, phase)
        );
    }

    private static float segment(float value, float start, float end) {
        return clamp((value - start) / (end - start));
    }

    private static float smooth(float value) {
        return value * value * (3.0f - 2.0f * value);
    }

    private static float cubicIn(float value) {
        return value * value * value;
    }

    private static float cubicOut(float value) {
        var inverse = 1.0f - value;
        return 1.0f - inverse * inverse * inverse;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    record Projection(Pose blackWing, Pose whiteWing, Pose ascension) {
    }

    record Pose(float radialScale, float lengthScale, float spreadDegrees, float pitchDegrees) {
        private static final Pose HIDDEN = new Pose(0.0f, 0.0f, 0.0f, 0.0f);

        boolean visible() {
            return radialScale > 0.001f && lengthScale > 0.001f;
        }
    }
}
