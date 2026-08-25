package org.academy.api.client.render.vfxgraph.arc;

/**
 * Blender「闪电附着」FloatCurve 权威控制点（从实际 {@code 闪电附着.blend} 提取，2026-08-23）。
 *
 * <p>这些曲线在 Blender 节点树中用于：弧拱成长（FloatCurve.001）、管半径沿弧剖面（FloatCurve.002）、
 * 管半径随 age 衰减（FloatCurve.005）、粒子缩放随生命衰减（FloatCurve.003）。复刻时以分段线性
 * 插值近似 AUTO 控制柄（对视觉足够）。</p>
 */
public final class BlenderArcCurves {
    private BlenderArcCurves() {
    }

    /** 弧拱成长曲线：输入 age/生命周期(0..1)，输出控制柄上推乘数。端点近平展 → 寿命末满拱。 */
    public static final float[][] ARCH_GROWTH = {
            {0.000f, 0.112f},
            {0.677f, 0.394f},
            {1.000f, 1.000f},
    };

    /** 管半径沿弧剖面：端粗 0.93 → 中细 0.475 → 端粗 0.925（复刻实测逐环半径）。 */
    public static final float[][] RADIUS_PROFILE = {
            {0.000f, 0.931f},
            {0.186f, 0.650f},
            {0.500f, 0.475f},
            {0.814f, 0.625f},
            {1.000f, 0.925f},
    };

    /** 管半径随 age 衰减：出生 1.0 → 临终 0（弧临死收缩）。 */
    public static final float[][] RADIUS_AGE = {
            {0.000f, 1.000f},
            {0.695f, 0.763f},
            {1.000f, 0.000f},
    };

    /** 粒子缩放随生命衰减：(0,1.0) → (0.659,0.769) → (1,0)。 */
    public static final float[][] PARTICLE_LIFE = {
            {0.000f, 1.000f},
            {0.659f, 0.769f},
            {1.000f, 0.000f},
    };

    /** 表面电弧噪声乘数 pa 沿弧剖面（Float Curve 无名，2026-08-23 从 .blend 提取）：脉冲——
     *  两端 0（噪声位移在端点消失，配合 Endpoint 吸附）、中段满幅 1。逐点再 × Random[0.4..2.2]。 */
    public static final float[][] NOISE_PA = {
            {0.000f, 0.000f},
            {0.100f, 0.000f},
            {0.100f, 1.000f},
            {0.900f, 1.000f},
            {0.900f, 0.000f},
            {1.000f, 0.000f},
    };

    /** 接触电弧噪声乘数 shapep 沿弧剖面（Float Curve.007，同 pa 脉冲）。 */
    public static final float[][] CONTACT_SHAPEP = NOISE_PA;

    /** 电弧亮度随 age（Float Curve.004）：Light = 曲线(age/寿命)×亮度 + 0.33×亮度 → 先亮后灭闪烁。
     *  (0,0)→(0.123,0.275)→(0.514,0.9125)→(0.796,0.594)→(1,0)。 */
    public static final float[][] LIGHT = {
            {0.000f, 0.000f},
            {0.1227f, 0.275f},
            {0.5136f, 0.9125f},
            {0.7955f, 0.5938f},
            {1.000f, 0.000f},
    };

    /** 接触电弧半径/发光随生命衰减（Float Curve.009）：接触弧管半径与 TLight 共用此曲线。 */
    public static final float[][] CONTACT_RADIUS_AGE = {
            {0.000f, 1.000f},
            {0.7136f, 0.7438f},
            {1.000f, 0.000f},
    };

    /** 表面电弧寿命沿弧变化（Float Curve.006）：删除阈值 = 曲线(factor)×3 + 20 帧（寿命沿弧变化）。 */
    public static final float[][] SURFACE_LIFE_VAR = {
            {0.000f, 0.000f},
            {0.1318f, 0.1875f},
            {0.4682f, 0.9437f},
            {0.8455f, 0.1938f},
            {1.000f, 0.000f},
    };

    /** 接触电弧寿命沿弧变化（Float Curve.008）：删除阈值 = 曲线(factor)×3 + 6 帧。 */
    public static final float[][] CONTACT_LIFE_VAR = {
            {0.0045f, 0.0125f},
            {0.4955f, 0.7625f},
            {1.000f, 0.000f},
    };

    /**
     * 对 FloatCurve 控制点做分段线性插值采样（AUTO 控制柄近似；x 越界钳制两端）。
     */
    public static float sample(float[][] points, float x) {
        if (points.length == 0) return 0f;
        if (x <= points[0][0]) return points[0][1];
        int n = points.length;
        if (x >= points[n - 1][0]) return points[n - 1][1];
        for (int i = 0; i < n - 1; i++) {
            if (x >= points[i][0] && x <= points[i + 1][0]) {
                float span = points[i + 1][0] - points[i][0];
                float t = span <= 1e-6f ? 0f : (x - points[i][0]) / span;
                return points[i][1] + (points[i + 1][1] - points[i][1]) * t;
            }
        }
        return points[n - 1][1];
    }
}