package org.academy.api.client.render.vfxgraph.arc;

/**
 * Blender「闪电附着」参考 fixture（2026-08-23 从实际 {@code 闪电附着.blend} 提取，权威）。
 *
 * <p>三个来源，全部来自 Blender 5.2 实际文件（非推测）：</p>
 * <ol>
 *   <li><b>modifier 实际生效参数</b>：{@code m.properties.inputs.Socket_xx.value}——
 *       GeometryNodes modifier 实例上存储的真实输入值（用户在界面调过的值，与界面默认/socket 缓存不同）；</li>
 *   <li><b>FloatCurve 控制点</b>：{@code CurveMapping.curves[0].points}——管半径剖面/弧拱成长曲线；</li>
 *   <li><b>实测几何</b>：frame40 逐步评估 modifier 后，对输出 mesh 按 PCA 主轴分组还原的管 spine
 *       （12 环中心）与逐环半径、以及各材质弧数。</li>
 * </ol>
 *
 * <p>复刻目标 = 亮蓝电光（用户确认），电弧颜色属性取 LColor 蓝 [0.13,0.21,1.0]。</p>
 */
public final class BlenderArcReference {
    private BlenderArcReference() {
    }

    // ==================== 场景 ====================

    /**
     * 地面平面：2×2（x/z ∈ [-1,1]，y=0），复刻 Blender Plane。
     */
    public static final float PLANE_SIZE = 2f;
    /**
     * 悬浮球世界位置：复刻 Blender Sphere loc (0.52, 0.38, 4.34)。
     */
    public static final float SPHERE_X = 0.52f;
    public static final float SPHERE_Y = 4.34f;
    public static final float SPHERE_Z = 0.38f;
    /**
     * 球半径 ≈1。
     */
    public static final float SPHERE_RADIUS = 1f;

    // ==================== modifier 实际生效参数（权威） ====================

    // --- 表面电弧面板 ---
    /**
     * 电弧密度（随机点云阵列 Density，单位面积点数）。
     */
    public static final float ARC_DENSITY = 1.0f;
    /**
     * 电弧粗细（Curve to Mesh Scale 乘数，管半径）。
     */
    public static final float ARC_THICKNESS = 0.78f;
    /**
     * 电弧宽度（实例 Scale 乘数）。
     */
    public static final float ARC_WIDTH = 1.0f;
    /**
     * 电弧高度（Curve Line 长度，弧基线跨度）。
     */
    public static final float ARC_HEIGHT = 1.0f;
    /**
     * 游离速度（噪声域扭曲速度）。
     */
    public static final float ARC_DRIFT = 1.5f;
    /**
     * 生命周期（帧）。
     */
    public static final float ARC_LIFETIME = 20f;
    /**
     * 电弧亮度（Light 属性，材质 Emission 乘 6）。
     */
    public static final float ARC_BRIGHTNESS = 1.0f;
    /**
     * 电弧颜色（LColor 属性；复刻目标改亮蓝）。
     */
    public static final float[] ARC_COLOR_BLUE = {0.13f, 0.21f, 1.0f, 1.0f};
    /**
     * 噪波强度（表面电弧）。
     */
    public static final float ARC_NOISE = 0.5f;
    /**
     * 随机点云阵列出现概率（保留 2.04%，每帧极稀疏）。
     */
    public static final float ARC_APPEAR_PROB = 0.0204f;
    /**
     * 随机点云阵列散布频率（帧门控用）。
     */
    public static final int ARC_SPREAD_FREQ = 30;

    // --- 接触闪电面板 ---
    /**
     * 接触范围（Sample Nearest Surface 距离剔除阈值）。
     */
    public static final float CONTACT_RANGE = 4.1f;
    /**
     * 接触闪电 Density（随机点云阵列.001）。
     */
    public static final float CONTACT_DENSITY = 1.47f;
    /**
     * 接触闪电生命周期（帧）。
     */
    public static final float CONTACT_LIFETIME = 6f;
    /**
     * 接触闪电发光强度（TLight，材质 Emission）。
     */
    public static final float CONTACT_EMISSION = 3.0f;
    /**
     * 接触闪电半径（管半径乘数）。
     */
    public static final float CONTACT_RADIUS = 0.8f;
    /**
     * 接触闪电噪波强度。
     */
    public static final float CONTACT_NOISE = 0.5f;
    /**
     * 接触闪电出现概率。
     */
    public static final float CONTACT_APPEAR_PROB = 0.15f;

    // --- 粒子面板 ---
    /**
     * 粒子密度（Curve to Points 随机删减保留率）。
     */
    public static final float PARTICLE_DENSITY = 0.48f;
    /**
     * 粒子缩放（实例 Scale 乘数）。
     */
    public static final float PARTICLE_SCALE = 0.83f;
    /**
     * 溅射速度（初始速度乘数）。
     */
    public static final float PARTICLE_SPLASH = 1.23f;
    /**
     * 重力G（Combine XYZ Z 分量，向下）。
     */
    public static final float PARTICLE_GRAVITY = -0.9f;
    /**
     * 粒子生命周期（帧）。
     */
    public static final float PARTICLE_LIFETIME = 30f;
    /**
     * 粒子亮度（PLight，材质 Emission 乘 6）。
     */
    public static final float PARTICLE_BRIGHTNESS = 1.0f;
    /**
     * 粒子颜色（PColor；复刻目标取材质 fallback 蓝 [0.23,0.35,0.69]）。
     */
    public static final float[] PARTICLE_COLOR_BLUE = {0.23f, 0.35f, 0.69f, 1.0f};

    // ==================== FloatCurve 控制点（权威，主源 BlenderArcCurves） ====================

    /**
     * FloatCurve.001 弧拱成长曲线（age/生命周期 → 控制柄上推乘数）。
     */
    public static final float[][] CURVE_ARCH = BlenderArcCurves.ARCH_GROWTH;
    /**
     * FloatCurve.002 管半径沿弧剖面（端粗中细）。
     */
    public static final float[][] CURVE_RADIUS = BlenderArcCurves.RADIUS_PROFILE;
    /**
     * FloatCurve.005 管半径随 age 衰减。
     */
    public static final float[][] CURVE_RADIUS_AGE = BlenderArcCurves.RADIUS_AGE;
    /**
     * FloatCurve.003 粒子缩放随生命衰减。
     */
    public static final float[][] CURVE_PARTICLE_LIFE = BlenderArcCurves.PARTICLE_LIFE;

    // --- M30 一比一复刻补充（2026-08-23 从实际 .blend 提取，先前缺失） ---

    /**
     * 表面电弧噪声乘数 pa 沿弧剖面（Float Curve 无名，脉冲：两端 0、中段 1）。
     */
    public static final float[][] CURVE_NOISE_PA = BlenderArcCurves.NOISE_PA;
    /**
     * 接触电弧噪声乘数 shapep（Float Curve.007，同 pa 脉冲）。
     */
    public static final float[][] CURVE_CONTACT_SHAPEP = BlenderArcCurves.CONTACT_SHAPEP;
    /**
     * 电弧亮度随 age（Float Curve.004，先亮后灭）。
     */
    public static final float[][] CURVE_LIGHT = BlenderArcCurves.LIGHT;
    /**
     * 接触电弧半径/发光生命（Float Curve.009）。
     */
    public static final float[][] CURVE_CONTACT_RADIUS_AGE = BlenderArcCurves.CONTACT_RADIUS_AGE;
    /**
     * 表面电弧寿命沿弧变化（Float Curve.006，删除阈值 = 曲线×3 + 20 帧）。
     */
    public static final float[][] CURVE_SURFACE_LIFE_VAR = BlenderArcCurves.SURFACE_LIFE_VAR;
    /**
     * 接触电弧寿命沿弧变化（Float Curve.008，删除阈值 = 曲线×3 + 6 帧）。
     */
    public static final float[][] CURVE_CONTACT_LIFE_VAR = BlenderArcCurves.CONTACT_LIFE_VAR;

    // ==================== 实测几何（frame40） ====================

    /**
     * 实测表面电弧管 spine（12 环中心，Blender 坐标）：从 (0.879,-0.736,0) 起，
     * 拱到 (0.892,-0.239,0.512)（高 ~0.51），回落 (1.000,0.110,0)。水平跨度 ~0.86。
     * 验证：生成弧的轮廓应与这个「平躺帐篷拱」同构（y=0 两端、中间高 ~0.5、水平跨度 ~0.85）。
     */
    public static final float[][] MEASURED_SPINE = {
            {0.879f, -0.736f, 0.000f},
            {0.886f, -0.684f, 0.114f},
            {0.762f, -0.416f, 0.210f},
            {0.804f, -0.403f, 0.307f},
            {0.824f, -0.396f, 0.416f},
            {0.869f, -0.320f, 0.479f},
            {0.892f, -0.239f, 0.512f},
            {0.885f, -0.221f, 0.496f},
            {0.973f, -0.155f, 0.497f},
            {0.909f, -0.009f, 0.343f},
            {0.993f, 0.058f, 0.114f},
            {1.000f, 0.110f, 0.000f},
    };

    /**
     * 实测表面电弧逐环管半径（最小 0.0024 / 最大 0.0034）。
     */
    public static final float[] MEASURED_RADIUS = {
            0.0034f, 0.0030f, 0.0028f, 0.0026f, 0.0025f, 0.0025f,
            0.0024f, 0.0024f, 0.0025f, 0.0026f, 0.0029f, 0.0034f,
    };

    /**
     * frame40 各材质弧数（实测）：表面电弧 2 条 / 接触闪电 4 条（+1 平面）/ 粒子 160 条。
     */
    public static final int MEASURED_SURFACE_ARCS = 2;
    public static final int MEASURED_CONTACT_ARCS = 4;
    public static final int MEASURED_PARTICLES = 160;

    // ==================== 曲线采样辅助 ====================

    /**
     * 对 FloatCurve 控制点做线性插值采样（AUTO 控制柄近似为分段线性；对复刻足够）。
     * 输出范围由控制点决定；x 超出 [min,max] 时钳制（Blender 默认 EXTRAPOLATED，这里钳制更稳）。
     */
    public static float sampleCurve(float[][] points, float x) {
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
