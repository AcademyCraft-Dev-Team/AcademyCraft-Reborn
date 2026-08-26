package org.academy.api.client.render.vfxgraph.arc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 曲线生成器（M22-Rev2）：复刻 Blender「闪电附着」的**两点闪电**。
 *
 * <p>参考几何节点树（闪电附着.blend）观感核心是一条**细、锯齿、白炽**的闪电折线，
 * 两端附着表面，主弧 from→to + 递归中点位移（fractal jagged）+ 轻微法线起拱，
 * 外加细分支。所有分支展平写入同一 ArcCurve（generation 标记层级、segment 标记连续折线段，
 * 建管时按 segment 分 run 避免分支被缝成一根管）。</p>
 */
public final class CurveGenerator {
    /**
     * 递归位移深度：控制锯齿细化层数。
     */
    private static final int JAGGED_DEPTH = 4;

    private CurveGenerator() {
    }

    /**
     * 兼容入口：以 (px,py,pz) 为中心、沿表面法线两侧生成一条两点闪电（含递归分支）。
     * 等价于 {@link #generateFromTo}，两点由中心沿法线 ±halfHeight 展开。
     */
    public static void generate(ArcCurve arc,
                                float px, float py, float pz,
                                float nx, float ny, float nz,
                                float width, int segments,
                                float r, float g, float b, float a,
                                float lifetime, long seed,
                                int branchDepth, int branchCount, float branchAngle,
                                float branchLengthScale, float branchWidthScale,
                                float branchBrightnessScale, float height) {
        float half = height * 0.5f;
        generateFromTo(arc,
                px - nx * half, py - ny * half, pz - nz * half,
                px + nx * half, py + ny * half, pz + nz * half,
                nx, ny, nz,
                width, segments,
                r, g, b, a, lifetime, seed,
                branchDepth, branchCount, branchAngle,
                branchLengthScale, branchWidthScale, branchBrightnessScale);
    }

    /**
     * 生成一条**表面短弧**（M30 复刻 Blender「闪电附着」主流水线）：
     * 平躺在表面的 Curve Line（长 = 电弧高度），Bezier 起拱控制柄沿**表面法线**上推，
     * 重采样到 {@code segments} 点。弧随 age 从近平展长成帐篷拱。
     *
     * <p>Blender 对应：{@code Curve Line(Start 0,0,-0.5 / End 0,0,0.5, 长度=电弧高度)} →
     * {@code Instance on Points}（Rotation=Align(axis=X, 法线)+AxisAngle(法线, 随机 0~2π)，
     * Scale=Random(0.4~1.2)×电弧宽度）→ {@code Realize} → {@code Set Spline Type(Bezier)} +
     * {@code Set Handle Positions(Offset = 表面法线 × FloatCurve.001(age/生命) × Random(0.4~1.2) × 电弧高度)} →
     * {@code Resample(Count=12)}。</p>
     *
     * <p>噪声位移与端点吸附由容器执行器每帧执行（{@code VfxSystemSimulator}：
     * NoiseAnimator + SurfaceConstraint）。弧拱基线存入 {@link ArcCurve}，供逐帧按 age 重采样
     * （复刻 Blender 每帧从基线几何重新求值）。</p>
     *
     * @param arc      目标弧线（清空后填充）
     * @param px,py,pz 表面附着点
     * @param nx,ny,nz 表面法线（弧沿法线上拱，基线躺在切平面）
     * @param height   电弧高度（Curve Line 长度）
     * @param curve    电弧粗细（控制柄上推基准乘数，相对高度比例）
     * @param width    管半径（两端 taper）
     * @param segments 重采样点数（Blender Resample Count=12）
     * @param seed     随机种子（决定随机轴角/起拱方位）
     */
    public static void generateSurfaceArc(ArcCurve arc,
                                          float px, float py, float pz,
                                          float nx, float ny, float nz,
                                          float height, float curve, float width, int segments,
                                          float r, float g, float b, float a,
                                          float lifetime, long seed) {
        arc.clearPoints();
        arc.setColor(r, g, b, a);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);

        // 随机轴角：绕法线旋转（Blender Axis Angle to Rotation，Axis=法线, Angle=Random 0~2π）
        var random = new Random(seed);
        float az = random.nextFloat() * 2f * (float) Math.PI;
        // 切平面参考轴（与法线不平行的任意轴 → 去法向分量 → 归一）
        float[] refAxis = Math.abs(ny) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float dot = refAxis[0] * nx + refAxis[1] * ny + refAxis[2] * nz;
        float t1x = refAxis[0] - dot * nx, t1y = refAxis[1] - dot * ny, t1z = refAxis[2] - dot * nz;
        float t1l = (float) Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
        if (t1l < 1e-6f) {
            t1x = 1;
            t1y = 0;
            t1z = 0;
            t1l = 1;
        } else {
            t1x /= t1l;
            t1y /= t1l;
            t1z /= t1l;
        }
        // 绕法线旋转 az：t1 旋转
        float c = (float) Math.cos(az), s = (float) Math.sin(az);
        float rx = t1x * c + (ny * t1z - nz * t1y) * s;
        float ry = t1y * c + (nz * t1x - nx * t1z) * s;
        float rz = t1z * c + (nx * t1y - ny * t1x) * s;

        // 每弧随机缩放（Blender Random Value 0.4~1.2，ID=curveID）
        float handleRandom = 0.4f + 0.8f * random.nextFloat();
        // 实例随机跨度缩放（Blender Instance Scale = Random[0.4..1.2]×电弧宽度，每弧一个）
        float spanScale = 0.4f + 0.8f * random.nextFloat();

        // 弧拱基线：平躺在表面、沿切平面方向 rx/ry/rz、长 = height（Curve Line 沿 local Z，
        // Align Rotation axis=X 使 local X → 法线、local Z 落在切平面，再绕法线随机旋转）
        arc.setArchBase(px, py, pz, nx, ny, nz, rx, ry, rz, handleRandom, height);
        arc.setArchSpanScale(spanScale);
        arc.setArchResample(curve, width, segments);

        // 首次采样（age=0：FloatCurve.001(0)=0.112 → 近平展）
        sampleSurfaceArch(arc);
    }

    public static void sampleSurfaceArch(ArcCurve arc) {
        float lifetime = Math.max(1e-3f, arc.lifetime());
        float ageFrac = Math.max(0f, Math.min(1f, arc.age() / lifetime));

        float nx = arc.archNx(), ny = arc.archNy(), nz = arc.archNz();
        float dx = arc.archDx(), dy = arc.archDy(), dz = arc.archDz();
        float half = arc.archHalf() * arc.archSpanScale();
        // M30 仿真区爬行：弧基座中心 = 附着点 + 累积游走偏移（Blender Set Position 语义，端点随后吸附拉回）
        float ax = arc.archX() + arc.wanderX();
        float ay = arc.archY() + arc.wanderY();
        float az = arc.archZ() + arc.wanderZ();
        float width = arc.archWidth();
        int segments = arc.archSegments();

        // 起点/终点：平躺在表面沿切平面 ±half（Blender Curve Line + Align 后 local Z 落在切平面）
        float sx = ax - dx * half, sy = ay - dy * half, sz = az - dz * half;
        float ex = ax + dx * half, ey = ay + dy * half, ez = az + dz * half;

        // 控制柄幅度：FloatCurve.001(ageFrac) × Random(0.4~1.2) × 电弧高度（M30 修正：**不含电弧粗细**，
        // 电弧粗细只缩放管半径）
        float growth = BlenderArcCurves.sample(BlenderArcCurves.ARCH_GROWTH, ageFrac);
        float handle = growth * arc.archRandom() * arc.archHeight();
        float hx = nx * handle, hy = ny * handle, hz = nz * handle;

        // 重采样 cubic Bezier 到 segments 点（两个控制柄同向量 → 对称帐篷拱）
        int n = Math.max(3, segments);
        arc.clearPoints();
        for (int i = 0; i < n; i++) {
            float t = (float) i / (n - 1);
            float inv = 1f - t;
            float w0 = inv * inv * inv;
            float w1 = 3f * inv * inv * t;
            float w2 = 3f * inv * t * t;
            float w3 = t * t * t;
            float bx = w0 * sx + w1 * (sx + hx) + w2 * (ex + hx) + w3 * ex;
            float by = w0 * sy + w1 * (sy + hy) + w2 * (ey + hy) + w3 * ey;
            float bz = w0 * sz + w1 * (sz + hz) + w2 * (ez + hz) + w3 * ez;

            // 管半径：表面弧 = FloatCurve.002(端粗中细) × FloatCurve.005(age 衰减)；
            // 接触弧 = 仅 FloatCurve.009(生命系数)（Blender Curve to Mesh.002 Scale）
            float radiusAge = arc.flatRadius()
                    ? BlenderArcCurves.sample(BlenderArcCurves.CONTACT_RADIUS_AGE, ageFrac)
                    : BlenderArcCurves.sample(BlenderArcCurves.RADIUS_AGE, ageFrac);
            float radiusProfile = arc.flatRadius()
                    ? 1f
                    : BlenderArcCurves.sample(BlenderArcCurves.RADIUS_PROFILE, t);
            arc.addPoint(bx, by, bz, width * radiusProfile * radiusAge, 0, 0);
            // M30 噪声乘数 pa = 脉冲曲线(spline因子) × Random[0.4..2.2]（确定性：每弧每点稳定，
            // Blender Store Named Attribute "pa"）
            float paVal = BlenderArcCurves.sample(BlenderArcCurves.NOISE_PA, t) * paRandom(arc.seed(), i);
            arc.setPa(arc.size() - 1, paVal);
        }
    }

    /**
     * M30：逐点噪声随机乘数（Blender Random Value.002[0.4..2.2]，ID 驱动 → 确定性）。
     */
    private static float paRandom(long seed, int i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return 0.4f + 1.8f * ((h & 0xFFFFFFFFL) / (float) 0xFFFFFFFFL);
    }

    /**
     * 生成一条**接触闪电弧**（M30 复刻 Blender 主组第二套系统）：
     * 从表面点 P 到接触表面最近点 N 的**直线弧**（无 Bezier 起拱），仅末端（N）吸附接触面。
     *
     * <p>Blender 对应：{@code Curve Line.002(Start 0,0,0 / End 0,0,1)}（垂直段，不旋转）→
     * {@code Set Position.004(Position=Sample Nearest Surface.002.Value, Selection=Endpoint End Size=1)}
     * → {@code Resample(12)} → 噪声 → {@code Curve to Mesh.002}。</p>
     *
     * <p>使用 {@link ArcCurve#setArchBase} 记录基线（P→N 直线），{@code curve=0} 使
     * {@link #sampleSurfaceArch} 生成直线而非帐篷拱；{@code flatRadius} 使半径仅随 age 衰减
     * （Blender FloatCurve.009）；{@code pinStart} 使端点吸附只作用于末端 N（Blender End Size=1）。</p>
     *
     * @param arc                           目标弧线
     * @param px,py,pz                      表面附着点（起点，固定在表面）
     * @param contactNx,contactNy,contactNz 接触表面最近点（末端，吸附接触面）
     * @param radius                        管半径基准（Blender Curve Circle r=0.01 × 接触闪电半径 0.8）
     * @param segments                      重采样点数（Blender Resample Count=12）
     * @param r,g,b,a                       颜色
     * @param lifetime                      生命周期（接触闪电 6 帧）
     * @param seed                          随机种子
     */
    public static void generateContactArc(ArcCurve arc,
                                          float px, float py, float pz,
                                          float contactNx, float contactNy, float contactNz,
                                          float radius, int segments,
                                          float r, float g, float b, float a,
                                          float lifetime, long seed) {
        arc.clearPoints();
        arc.setColor(r, g, b, a);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);

        // 基线方向：P → N（直线弧，无拱）。长度 |P-N|。
        float dx = contactNx - px, dy = contactNy - py, dz = contactNz - pz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) len = 1f;
        dx /= len;
        dy /= len;
        dz /= len;

        // 基线：中心 = (P+N)/2，方向 = 直线方向，长 = |P-N|；curve=0 → 直线；flatRadius → 半径仅 age 衰减
        float cx = (px + contactNx) * 0.5f;
        float cy = (py + contactNy) * 0.5f;
        float cz = (pz + contactNz) * 0.5f;
        arc.setArchBase(cx, cy, cz, 0, 0, 0, dx, dy, dz, 1f, len);
        arc.setArchResample(0f, radius, segments);
        arc.setFlatRadius(true);
        arc.setPinStart(true);

        // 首次采样（直线，age=0 满半径）
        sampleSurfaceArch(arc);
    }

    /**
     * 从 from→to 两点 + 表面法线生成一条**两点闪电**（Blender 流水线核心），含递归分支。
     *
     * <p>主弧：两端点固定（附着表面），经递归中点位移成锯齿闪电，中部沿法线轻微起拱，
     * 再重采样到 {@code segments} 点。宽度 taper 中间鼓两端收。</p>
     */
    public static void generateFromTo(ArcCurve arc,
                                      float fromX, float fromY, float fromZ,
                                      float toX, float toY, float toZ,
                                      float nx, float ny, float nz,
                                      float width, int segments,
                                      float r, float g, float b, float a,
                                      float lifetime, long seed,
                                      int branchDepth, int branchCount, float branchAngle,
                                      float branchLengthScale, float branchWidthScale,
                                      float branchBrightnessScale) {
        arc.clearPoints();
        arc.setColor(r, g, b, a);
        arc.setLifetime(lifetime);
        arc.setSeed(seed);

        var random = new Random(seed);
        int[] segmentCounter = {0};

        // 主弧：两点锯齿闪电 + 轻微法线起拱
        generateBolt(arc, fromX, fromY, fromZ, toX, toY, toZ,
                nx, ny, nz, width, segments, 0, random, segmentCounter);

        // 递归分支（附着在主弧中部控制点）
        if (branchDepth > 0 && branchCount > 0) {
            generateBranchesRecursive(arc, 0, branchDepth, branchCount,
                    branchAngle, branchLengthScale, branchWidthScale,
                    branchBrightnessScale, segments,
                    fromX, fromY, fromZ, toX, toY, toZ, nx, ny, nz,
                    r, g, b, a, random, segmentCounter);
        }
    }

    /**
     * 生成一条 from→to 的两点闪电折线（递归中点位移 fractal jagged + 轻微法线起拱）。
     *
     * <p>位移量随深度递减（fractal），得到粗细变化的锯齿闪电。起拱幅度取弦长的很小比例，
     * 避免变成平滑大弓。</p>
     */
    private static void generateBolt(ArcCurve arc,
                                     float fromX, float fromY, float fromZ,
                                     float toX, float toY, float toZ,
                                     float nx, float ny, float nz,
                                     float width, int segments, float generation,
                                     Random random, int[] segmentCounter) {
        int segment = segmentCounter[0]++;
        float dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        float chordLen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (chordLen < 1e-6f) chordLen = 1f;

        // 垂直方向基（位移方向）：切平面内随机 + 轻微法线分量（起拱）
        float tx = dx / chordLen, ty = dy / chordLen, tz = dz / chordLen;
        // 找与切线不共线的参考轴
        float rx, ry, rz;
        if (Math.abs(ty) < 0.9f) {
            rx = 0;
            ry = 1;
            rz = 0;
        } else {
            rx = 1;
            ry = 0;
            rz = 0;
        }
        // 参考轴去切向分量 → 切平面内的一根轴
        float dot = rx * tx + ry * ty + rz * tz;
        float ux = rx - dot * tx, uy = ry - dot * ty, uz = rz - dot * tz;
        float ulen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ulen < 1e-6f) {
            ux = 1;
            uy = 0;
            uz = 0;
        } else {
            ux /= ulen;
            uy /= ulen;
            uz /= ulen;
        }

        // 递归中点位移生成锯齿点列
        var pts = new ArrayList<float[]>();
        pts.add(new float[]{fromX, fromY, fromZ});
        midpointDisplace(pts, fromX, fromY, fromZ, toX, toY, toZ,
                ux, uy, uz, nx, ny, nz, chordLen, 0, random);
        pts.add(new float[]{toX, toY, toZ});

        // 重采样到 segments 点（等距）
        int n = Math.max(3, segments);
        var sampled = new ArrayList<float[]>(n);
        for (int i = 0; i < n; i++) {
            float t = (float) i / (n - 1);
            sampled.add(sampleAlong(pts, t));
        }

        // 宽度 taper：中间鼓、两端收
        for (int i = 0; i < n; i++) {
            float t = (float) i / (n - 1);
            float taper = (float) Math.sin(t * Math.PI);
            taper = 0.3f + 0.7f * taper;
            float w = width * taper;
            var p = sampled.get(i);
            arc.addPoint(p[0], p[1], p[2], w, generation, segment);
        }
    }

    /**
     * 递归中点位移：把 from→to 的线段在中点沿垂直方向随机偏移，深度递减。
     */
    private static void midpointDisplace(List<float[]> pts,
                                         float ax, float ay, float az,
                                         float bx, float by, float bz,
                                         float ux, float uy, float uz,
                                         float nx, float ny, float nz,
                                         float chordLen, int depth, Random random) {
        if (depth >= JAGGED_DEPTH) return;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        // 位移幅度：随深度递减（fractal）；沿切平面随机 + 轻微法线起拱
        float amp = chordLen * (0.12f / (depth + 1));
        // 随机方位角
        float ang = random.nextFloat() * 2f * (float) Math.PI;
        float px = ux * (float) Math.cos(ang) + nx * 0.3f;
        float py = uy * (float) Math.cos(ang) + ny * 0.3f;
        float pz = uz * (float) Math.cos(ang) + nz * 0.3f;
        float plen = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (plen < 1e-6f) {
            px = ux;
            py = uy;
            pz = uz;
        } else {
            px /= plen;
            py /= plen;
            pz /= plen;
        }
        float ox = mx + px * amp, oy = my + py * amp, oz = mz + pz * amp;
        midpointDisplace(pts, ax, ay, az, ox, oy, oz, ux, uy, uz, nx, ny, nz, chordLen, depth + 1, random);
        pts.add(new float[]{ox, oy, oz});
        midpointDisplace(pts, ox, oy, oz, bx, by, bz, ux, uy, uz, nx, ny, nz, chordLen, depth + 1, random);
    }

    /**
     * 沿点列按弧长参数 t 取点（简单折线线性插值）。
     */
    private static float[] sampleAlong(List<float[]> pts, float t) {
        float total = 0f;
        float[] segLen = new float[Math.max(1, pts.size() - 1)];
        for (int i = 0; i < pts.size() - 1; i++) {
            float d = (float) Math.sqrt(
                    Math.pow(pts.get(i + 1)[0] - pts.get(i)[0], 2)
                            + Math.pow(pts.get(i + 1)[1] - pts.get(i)[1], 2)
                            + Math.pow(pts.get(i + 1)[2] - pts.get(i)[2], 2));
            segLen[i] = d;
            total += d;
        }
        if (total < 1e-6f) return pts.get(0);
        float target = t * total;
        float acc = 0f;
        for (int i = 0; i < segLen.length; i++) {
            if (acc + segLen[i] >= target || i == segLen.length - 1) {
                float lt = segLen[i] < 1e-6f ? 0f : (target - acc) / segLen[i];
                lt = Math.max(0f, Math.min(1f, lt));
                float[] a = pts.get(i), b = pts.get(i + 1);
                return new float[]{
                        a[0] + (b[0] - a[0]) * lt,
                        a[1] + (b[1] - a[1]) * lt,
                        a[2] + (b[2] - a[2]) * lt};
            }
            acc += segLen[i];
        }
        return pts.get(pts.size() - 1);
    }

    /**
     * 递归生成分支：沿主弧控制柄方向，在主弧中部控制点附着子弧。
     */
    private static void generateBranchesRecursive(ArcCurve arc,
                                                  int currentGen, int maxDepth,
                                                  int branchCount, float branchAngle,
                                                  float lengthScale, float widthScale,
                                                  float brightnessScale, int segments,
                                                  float fromX, float fromY, float fromZ,
                                                  float toX, float toY, float toZ,
                                                  float nx, float ny, float nz,
                                                  float r, float g, float b, float a,
                                                  Random random, int[] segmentCounter) {
        if (currentGen >= maxDepth) return;

        float childGen = currentGen + 1;

        var genPoints = new ArrayList<int[]>();
        for (int i = 0; i < arc.size(); i++) {
            if (Math.abs(arc.generation(i) - currentGen) < 0.01f) {
                genPoints.add(new int[]{i});
            }
        }
        if (genPoints.isEmpty()) return;

        int total = genPoints.size();
        int attachCount = Math.min(branchCount, Math.max(0, total - 2));
        if (attachCount <= 0) return;

        for (int bi = 0; bi < attachCount; bi++) {
            float t = 0.2f + 0.6f * (float) bi / Math.max(1, attachCount - 1);
            int idx = Math.max(1, Math.min(total - 2, (int) Math.round(t * (total - 1))));
            int pointIdx = genPoints.get(idx)[0];

            float bx = arc.x(pointIdx);
            float by = arc.y(pointIdx);
            float bz = arc.z(pointIdx);

            int prev = Math.max(0, pointIdx - 1);
            int next = Math.min(arc.size() - 1, pointIdx + 1);
            float tx = arc.x(next) - arc.x(prev);
            float ty = arc.y(next) - arc.y(prev);
            float tz = arc.z(next) - arc.z(prev);
            float tlen = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tlen < 1e-6f) {
                tx = 0;
                ty = 1;
                tz = 0;
            } else {
                tx /= tlen;
                ty /= tlen;
                tz /= tlen;
            }

            float[] branchDir = SurfaceDistributor.tangentDirection(tx, ty, tz, branchAngle, random);
            float branchNx = branchDir[0], branchNy = branchDir[1], branchNz = branchDir[2];

            float parentWidth = arc.width(pointIdx) / (0.3f + 0.7f * (float) Math.sin(
                    (float) idx / Math.max(1, total - 1) * Math.PI));
            float cw = parentWidth * widthScale;

            float lx = toX - fromX, ly = toY - fromY, lz = toZ - fromZ;
            float chordLen = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
            float childLen = Math.max(chordLen * lengthScale, 1e-3f);

            float eX = bx + branchNx * childLen;
            float eY = by + branchNy * childLen;
            float eZ = bz + branchNz * childLen;

            generateBolt(arc, bx, by, bz, eX, eY, eZ,
                    branchNx, branchNy, branchNz,
                    cw, segments, childGen, random, segmentCounter);

            generateBranchesRecursive(arc, (int) childGen, maxDepth,
                    branchCount, branchAngle, lengthScale, widthScale,
                    brightnessScale, segments,
                    bx, by, bz, eX, eY, eZ, branchNx, branchNy, branchNz,
                    r, g, b, a, random, segmentCounter);
        }
    }
}
