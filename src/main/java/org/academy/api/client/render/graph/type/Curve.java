package org.academy.api.client.render.graph.type;

import java.util.List;

/**
 * 曲线（值载体）。按时间采样的一维关键帧序列（VFX 图使用）。
 *
 * <p>每个关键帧携带进入该帧的插值模式与 in/out 切线（bezier 用）。切线为
 * 「值/时间」斜率（Δvalue/Δtime，类似 Unity 动画曲线）。</p>
 */
public record Curve(List<Keyframe> keyframes) {
    public Curve {
        keyframes = List.copyOf(keyframes);
    }

    /**
     * 进入该关键帧的插值模式。
     */
    public enum Interpolation {
        LINEAR, STEP, SMOOTH, BEZIER
    }

    public record Keyframe(float time, float value, float inTangent, float outTangent, Interpolation interpolation) {
        /**
         * 便捷构造：无切线、线性。
         */
        public Keyframe(float time, float value) {
            this(time, value, 0f, 0f, Interpolation.LINEAR);
        }

        /**
         * 便捷构造：线性插值入段。
         */
        public static Keyframe linear(float time, float value) {
            return new Keyframe(time, value, 0f, 0f, Interpolation.LINEAR);
        }

        /**
         * 便捷构造：步进插值入段。
         */
        public static Keyframe step(float time, float value) {
            return new Keyframe(time, value, 0f, 0f, Interpolation.STEP);
        }

        /**
         * 便捷构造：平滑（smoothstep）插值入段。
         */
        public static Keyframe smooth(float time, float value) {
            return new Keyframe(time, value, 0f, 0f, Interpolation.SMOOTH);
        }
    }
}
