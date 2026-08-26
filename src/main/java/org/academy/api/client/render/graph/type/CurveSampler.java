package org.academy.api.client.render.graph.type;

import org.academy.api.client.render.graph.type.Curve.Keyframe;

/**
 * 曲线采样器（CPU）：按插值模式对关键帧段求值。
 * 插值模式挂在「进入」该关键帧上（STEP=保持上一帧值）。
 */
public final class CurveSampler {
    private CurveSampler() {
    }

    /**
     * 采样 [0,1] 内 t；越界返回首/末关键帧值。
     */
    public static float sample(Curve curve, float t) {
        var kfs = curve.keyframes();
        if (kfs.isEmpty()) return 0f;
        if (kfs.size() == 1) return kfs.get(0).value();
        if (t <= kfs.get(0).time()) return kfs.get(0).value();
        var last = kfs.get(kfs.size() - 1);
        if (t >= last.time()) return last.value();
        for (int i = 1; i < kfs.size(); i++) {
            var kf = kfs.get(i);
            if (t < kf.time()) {
                return sampleSegment(kfs.get(i - 1), kf, t);
            }
        }
        return last.value();
    }

    private static float sampleSegment(Keyframe a, Keyframe b, float t) {
        float dt = b.time() - a.time();
        if (dt <= 0f) return b.value();
        float u = (t - a.time()) / dt;
        return switch (b.interpolation()) {
            case STEP -> a.value();
            case SMOOTH -> {
                float s = u * u * (3f - 2f * u);
                yield a.value() + (b.value() - a.value()) * s;
            }
            case BEZIER -> {
                float m0 = a.outTangent() * dt;
                float m1 = b.inTangent() * dt;
                float u2 = u * u;
                float u3 = u2 * u;
                yield (2f * u3 - 3f * u2 + 1f) * a.value()
                        + (u3 - 2f * u2 + u) * m0
                        + (-2f * u3 + 3f * u2) * b.value()
                        + (u3 - u2) * m1;
            }
            default -> a.value() + (b.value() - a.value()) * u; // LINEAR
        };
    }
}
