package org.academy.api.client.render.vfxgraph.arc;

/**
 * 单条电弧（M22-Rev2）：控制点序列 + 每点宽度 + 分支层级 + 颜色 + 生命周期。
 *
 * <p>所有分支（主弧 + 递归子弧）展平存储在同一组数组中，通过 {@link #generation} 区分层级。
 * CPU 只产宏观 spine（默认 12 控制点/分支），锯齿/噪声/辉光由着色器承担。</p>
 */
public final class ArcCurve {
    private static final int INITIAL = 16;

    private float[] px = new float[INITIAL];
    private float[] py = new float[INITIAL];
    private float[] pz = new float[INITIAL];
    private float[] width = new float[INITIAL];
    private float[] gen = new float[INITIAL];
    private int[] seg = new int[INITIAL];
    /** 逐点噪声乘数 pa（M30 复刻 Blender Store Named Attribute "pa"）：
     *  表面/接触弧 = 脉冲曲线(spline因子)×Random[0.4..2.2]；自由弧默认 1（legacy 满幅）。 */
    private float[] pa = new float[INITIAL];
    private int size;

    /** 每个控制点的**基准位置**（M29b 修复）：生成时 = 初始位置，噪声动画/表面吸附每帧
     *  只改当前位置 {@code px/py/pz}，基准不变——位移相对基准计算，避免逐帧累积漂移。 */
    private float[] bx = new float[INITIAL];
    private float[] by = new float[INITIAL];
    private float[] bz = new float[INITIAL];

    private float r, g, b, a;
    private float age;
    private float lifetime;
    private long seed;

    /** 可选的端点吸附表面（三角形 xyz*3/三角形；null = 自由弧不做表面吸附）。 */
    private float[] surface;

    /** 本帧新增标记（M29b-02）：{@link ArcBuffer#add} 置 true，{@link ArcBuffer#advance} 开头清全量。
     *  供 arc_spark 只从本帧新增弧派生火花，消除指数放大。 */
    private boolean fresh;

    /** 逐弧噪声强度（M30：spawn 块噪声属性接线；NaN = 未设置，用模拟器默认）。 */
    private float noiseStrength = Float.NaN;
    /** 逐弧游离速度（M30：仿真区爬行步长乘数；NaN = 未设置，用模拟器默认）。 */
    private float driftSpeed = Float.NaN;

    /** 仿真区爬行游走累积偏移（M30 复刻 Blender Set Position：弧基座每帧沿切平面随机滑移）。
     *  采样弧拱时中心 = archBase + wander，端点随后被表面吸附拉回。 */
    private float wanderX, wanderY, wanderZ;

    // --- Blender 弧拱基线（M30：复刻 Set Spline Type(Bezier) + Set Handle Positions 逐帧求值） ---
    /** 表面附着点（基线中心，弧沿其法线/切平面展开）。 */
    private float archX, archY, archZ;
    /** 表面法线（控制柄上推方向）。 */
    private float archNx, archNy, archNz;
    /** 基线切平面方向（单位向量，弧平躺在表面沿此方向展开）。 */
    private float archDx, archDy, archDz;
    /** 每弧随机缩放（Blender Random Value 0.4~1.2，ID=curveID）。 */
    private float archRandom = 1f;
    /** 弧高（Curve Line 长度）。 */
    private float archHeight = 1f;
    /** 基线半长（height/2）。 */
    private float archHalf = 0.5f;
    /** 每弧实例随机跨度缩放（M30 复刻 Blender Instance Scale = Random[0.4..1.2]×电弧宽度：
     *  弧基线跨度随弧随机 0.4~1.2×，观感大小各异）。 */
    private float archSpanScale = 1f;
    /** 电弧粗细（控制柄上推基准乘数，0.78）。 */
    private float archCurve = 0.78f;
    /** 管半径基准（逐帧重采样 width）。 */
    private float archWidth = 0.01f;
    /** 重采样点数（Blender Resample Count=12）。 */
    private int archSegments = 12;
    /** 是否设置了弧拱基线（需逐帧重采样）。 */
    private boolean hasArch;
    /** 接触弧：起点固定在表面点，不参与 surface 端点吸附（Blender End Size=1 仅末端吸附）。 */
    private boolean pinStart;
    /** 接触弧：管半径仅随 age 衰减（FloatCurve.009），无沿弧剖面（FloatCurve.002）端粗中细。 */
    private boolean flatRadius;
    /** 火花粒子速度（Blender 粒子模拟：位置 += 速度×dt + 重力×dt²；null 或未设置 = 非粒子）。 */
    private float[] sparkVelocity;

    public ArcCurve() {
    }

    /** 清空控制点（复用数组）。 */
    public void clearPoints() {
        size = 0;
    }

    /** 复位全部逐弧模拟状态（池化复用：{@link ArcBuffer#add} 时调用，防上一条弧的残留状态泄漏到新弧）。 */
    public void resetSimState() {
        this.wanderX = 0f;
        this.wanderY = 0f;
        this.wanderZ = 0f;
        this.pinStart = false;
        this.flatRadius = false;
        this.hasArch = false;
        this.sparkVelocity = null;
        this.surface = null;
        this.noiseStrength = Float.NaN;
        this.driftSpeed = Float.NaN;
        this.archRandom = 1f;
        this.archHeight = 1f;
        this.archHalf = 0.5f;
        this.archSpanScale = 1f;
        this.archCurve = 0.78f;
        this.archWidth = 0.01f;
        this.archSegments = 12;
    }

    /** 追加一个控制点（segment 默认 0 = 单段连续折线）。 */
    public void addPoint(float x, float y, float z, float w, float generation) {
        addPoint(x, y, z, w, generation, 0);
    }

    /**
     * 追加一个控制点。
     *
     * @param segment 连续折线段 id：相邻同 segment 的点才构成连续 tube；分支等不连续段用不同 id，
     *                建管时按 segment 分 run，避免把互不相连的段缝成一根管。
     */
    public void addPoint(float x, float y, float z, float w, float generation, int segment) {
        if (size == px.length) {
            int cap = px.length * 2;
            px = java.util.Arrays.copyOf(px, cap);
            py = java.util.Arrays.copyOf(py, cap);
            pz = java.util.Arrays.copyOf(pz, cap);
            width = java.util.Arrays.copyOf(width, cap);
            gen = java.util.Arrays.copyOf(gen, cap);
            seg = java.util.Arrays.copyOf(seg, cap);
            bx = java.util.Arrays.copyOf(bx, cap);
            by = java.util.Arrays.copyOf(by, cap);
            bz = java.util.Arrays.copyOf(bz, cap);
            pa = java.util.Arrays.copyOf(pa, cap);
        }
        px[size] = x;
        py[size] = y;
        pz[size] = z;
        bx[size] = x;
        by[size] = y;
        bz[size] = z;
        width[size] = w;
        gen[size] = generation;
        seg[size] = segment;
        pa[size] = 1f;
        size++;
    }

    public int size() {
        return size;
    }

    public float x(int i) {
        return px[i];
    }

    public float y(int i) {
        return py[i];
    }

    public float z(int i) {
        return pz[i];
    }

    public float width(int i) {
        return width[i];
    }

    public float generation(int i) {
        return gen[i];
    }

    /** 控制点所属连续折线段 id（分支等不连续段为不同值，供建管分 run）。 */
    public int segment(int i) {
        return seg[i];
    }

    /** 修改指定控制点的位置（只改当前位置，基准不变——供噪声动画/表面吸附）。 */
    public void setPoint(int i, float x, float y, float z) {
        px[i] = x;
        py[i] = y;
        pz[i] = z;
    }

    /** 基准位置（M29b：生成时的初始位置，噪声位移相对它计算，防累积漂移）。 */
    public float baseX(int i) {
        return bx[i];
    }

    /** 基准位置（M29b）。 */
    public float baseY(int i) {
        return by[i];
    }

    /** 基准位置（M29b）。 */
    public float baseZ(int i) {
        return bz[i];
    }

    /** 逐点噪声乘数 pa（M30 Blender Store Named Attribute "pa"）；自由弧默认 1。 */
    public float pa(int i) {
        return pa[i];
    }

    /** 设置逐点噪声乘数（M30）。 */
    public void setPa(int i, float pa) {
        this.pa[i] = pa;
    }

    /** 逐弧噪声强度（M30）；NaN = 未设置（用模拟器默认）。 */
    public float noiseStrength() {
        return noiseStrength;
    }

    /** 是否设置了逐弧噪声强度。 */
    public boolean hasNoiseStrength() {
        return !Float.isNaN(noiseStrength);
    }

    /** 设置逐弧噪声强度（spawn 块 noise_strength 属性）。 */
    public void setNoiseStrength(float noiseStrength) {
        this.noiseStrength = noiseStrength;
    }

    /** 逐弧游离速度（M30 仿真区爬行步长乘数）；NaN = 未设置（用模拟器默认）。 */
    public float driftSpeed() {
        return driftSpeed;
    }

    /** 是否设置了逐弧游离速度。 */
    public boolean hasDriftSpeed() {
        return !Float.isNaN(driftSpeed);
    }

    /** 设置逐弧游离速度（spawn 块 drift_speed 属性）。 */
    public void setDriftSpeed(float driftSpeed) {
        this.driftSpeed = driftSpeed;
    }

    /** 仿真区爬行游走累积偏移 X（M30 Set Position 语义，中心 = archBase + wander）。 */
    public float wanderX() {
        return wanderX;
    }

    /** 仿真区爬行游走累积偏移 Y。 */
    public float wanderY() {
        return wanderY;
    }

    /** 仿真区爬行游走累积偏移 Z。 */
    public float wanderZ() {
        return wanderZ;
    }

    /** 累积仿真区爬行游走偏移（每帧切平面随机滑移，端点随后被表面吸附拉回）。 */
    public void accumulateWander(float dx, float dy, float dz) {
        this.wanderX += dx;
        this.wanderY += dy;
        this.wanderZ += dz;
    }

    // --- 颜色 ---

    public float r() {
        return r;
    }

    public float g() {
        return g;
    }

    public float b() {
        return b;
    }

    public float a() {
        return a;
    }

    public void setColor(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    // --- 生命周期 ---

    public float age() {
        return age;
    }

    public void setAge(float age) {
        this.age = age;
    }

    public float lifetime() {
        return lifetime;
    }

    public void setLifetime(float lifetime) {
        this.lifetime = lifetime;
    }

    public boolean isAlive() {
        return age < lifetime;
    }

    // --- 种子 ---

    public long seed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    /** 设置端点吸附表面（三角形数组；null = 自由弧）。 */
    public void setSurface(float[] triangles) {
        this.surface = triangles;
    }

    /** 获取端点吸附表面；null = 自由弧。 */
    public float[] surface() {
        return surface;
    }

    /** 是否带表面（需要端点吸附）。 */
    public boolean hasSurface() {
        return surface != null && surface.length > 0;
    }

    /** 本帧新增标记（M29b-02）：spawn 块本帧 {@code arcs().add()} 的弧为 true。 */
    public boolean fresh() {
        return fresh;
    }

    /** 设置本帧新增标记。 */
    public void setFresh(boolean fresh) {
        this.fresh = fresh;
    }

    // --- Blender 弧拱基线（M30） ---

    /** 设置弧拱基线：表面附着点、法线、切平面方向、每弧随机缩放、弧高。 */
    public void setArchBase(float x, float y, float z,
                            float nx, float ny, float nz,
                            float dx, float dy, float dz,
                            float randomScale, float height) {
        this.archX = x;
        this.archY = y;
        this.archZ = z;
        this.archNx = nx;
        this.archNy = ny;
        this.archNz = nz;
        this.archDx = dx;
        this.archDy = dy;
        this.archDz = dz;
        this.archRandom = randomScale;
        this.archHeight = Math.max(1e-3f, height);
        this.archHalf = this.archHeight * 0.5f;
        this.hasArch = true;
    }

    /** 设置弧拱重采样参数（电弧粗细/管半径基准/段数）。 */
    public void setArchResample(float curve, float width, int segments) {
        this.archCurve = curve;
        this.archWidth = width;
        this.archSegments = Math.max(3, segments);
    }

    /** 实例随机跨度缩放（M30 Blender Instance Scale Random[0.4..1.2]×电弧宽度）。 */
    public float archSpanScale() {
        return archSpanScale;
    }

    /** 设置实例随机跨度缩放（generateSurfaceArc 每弧一个 Random[0.4..1.2]）。 */
    public void setArchSpanScale(float scale) {
        this.archSpanScale = scale;
    }

    /** 是否跳过起点表面吸附（接触弧 Blender End Size=1）。 */
    public boolean pinStart() {
        return pinStart;
    }

    /** 设置是否跳过起点表面吸附（接触弧用）。 */
    public void setPinStart(boolean pinStart) {
        this.pinStart = pinStart;
    }

    /** 管半径是否仅 age 衰减无沿弧剖面（接触弧 Blender FloatCurve.009）。 */
    public boolean flatRadius() {
        return flatRadius;
    }

    /** 设置管半径是否仅 age 衰减（接触弧用）。 */
    public void setFlatRadius(boolean flatRadius) {
        this.flatRadius = flatRadius;
    }

    /** 火花粒子初速度；null = 非粒子弧（普通弧/接触弧）。 */
    public float[] sparkVelocity() {
        return sparkVelocity;
    }

    /** 设置火花粒子初速度（x,y,z）。 */
    public void setSparkVelocity(float vx, float vy, float vz) {
        this.sparkVelocity = new float[]{vx, vy, vz};
    }

    /** 是否设置弧拱基线（Blender 弧逐帧重采样）。 */
    public boolean hasArchBase() {
        return hasArch;
    }

    /** 清除弧拱基线（自由弧/火花用）。 */
    public void clearArchBase() {
        this.hasArch = false;
    }

    public float archX() {
        return archX;
    }

    public float archY() {
        return archY;
    }

    public float archZ() {
        return archZ;
    }

    public float archNx() {
        return archNx;
    }

    public float archNy() {
        return archNy;
    }

    public float archNz() {
        return archNz;
    }

    public float archDx() {
        return archDx;
    }

    public float archDy() {
        return archDy;
    }

    public float archDz() {
        return archDz;
    }

    public float archRandom() {
        return archRandom;
    }

    public float archHeight() {
        return archHeight;
    }

    public float archHalf() {
        return archHalf;
    }

    public float archCurve() {
        return archCurve;
    }

    public float archWidth() {
        return archWidth;
    }

    public int archSegments() {
        return archSegments;
    }

    /** 将指定范围的控制点拷贝到目标（供子弧生成等使用）；基准位置一并拷贝。 */
    public void copyRange(ArcCurve target, int from, int to) {
        for (int i = from; i < to; i++) {
            int idx = target.size();
            target.addPoint(px[i], py[i], pz[i], width[i], gen[i], seg[i]);
            target.bx[idx] = bx[i];
            target.by[idx] = by[i];
            target.bz[idx] = bz[i];
            target.pa[idx] = pa[i];
        }
    }
}
