package org.academy.api.client.render.vfxgraph.arc;

/**
 * 噪声位移动画器（M22-Rev2 / M30 复刻）：Blender「闪电附着」的 Noise Texture + Scene Time。
 *
 * <p>参考几何节点树：{@code Position + (1,1,1)×SceneTime.Seconds} → {@code Noise Texture}
 * （Scale=2, Detail=2, Roughness=0.5，3D 值噪声）→ 颜色向量 → {@code × pa（逐点乘数）} →
 * {@code × 噪波强度} → {@code Set Position.001 Offset}。</p>
 *
 * <p><b>M30 修正（一比一复刻）</b>：噪声域扭曲 = {@code Position + 场景秒×(1,1,1)}（漂移不乘
 * 游离速度——游离速度只驱动仿真区爬行 Set Position）；噪声乘数用逐点 {@code pa}（表面/接触弧 =
 * 脉冲曲线(spline因子)×Random[0.4..2.2]，端点 0；自由弧默认 1）。幅度 = (noise−0.5)×pa×噪波强度。</p>
 *
 * <p><b>M29b 修复（防累积漂移）</b>：位移恒相对 {@link ArcCurve#baseX(int)} 等**基准位置**计算
 * （{@code pos = base + noise(base)}），每帧在基准附近摆动、不漂移（与 Blender 每帧从基线几何
 * 重新求值语义一致）。</p>
 */
public final class NoiseAnimator {
    private NoiseAnimator() {
    }

    /**
     * 对 ArcCurve 的所有控制点施加低频噪声位移（相对基准位置，M29b 防累积漂移）。
     *
     * @param arc           目标弧线
     * @param time          当前时间（场景秒，驱动噪声域漂移 ×(1,1,1)）
     * @param driftSpeed    保留参数（Blender 游离速度只驱动仿真区爬行，本方法不再使用）
     * @param noiseStrength 噪波强度（默认 0.5，Blender 噪波强度 socket）
     * @param noiseScale    噪声频率（默认 2.0，同 Blender Noise Scale）
     * @param seed          噪声种子
     */
    public static void animate(ArcCurve arc, float time, float driftSpeed,
                               float noiseStrength, float noiseScale, long seed) {
        if (noiseStrength < 1e-6f) return;

        // Blender: noise Vector = Position + SceneTime.Seconds×(1,1,1)（三轴同向漂移，随场景时间流动）
        float t = time;
        for (int i = 0; i < arc.size(); i++) {
            float px = arc.baseX(i);
            float py = arc.baseY(i);
            float pz = arc.baseZ(i);

            // 3D 值噪声（Scale=2, Detail=2, Roughness=0.5），输入 = 基准位置 + 场景秒
            float nx = valueNoise3D(px * noiseScale + t, py * noiseScale + t, pz * noiseScale + t, seed);
            float ny = valueNoise3D(px * noiseScale + t, py * noiseScale + t, pz * noiseScale + t, seed + 1000);
            float nz = valueNoise3D(px * noiseScale + t, py * noiseScale + t, pz * noiseScale + t, seed + 2000);

            // 位移 = (noise−0.5) × pa(逐点) × 噪波强度；pa 端点 0（表面/接触弧）→ 端点不动，
            // 与 Blender Set Position.001 Selection=NOT(Endpoint) 一致；位移相对基准，逐帧不累积
            float amp = arc.pa(i) * noiseStrength;
            arc.setPoint(i,
                    px + (nx - 0.5f) * amp,
                    py + (ny - 0.5f) * amp,
                    pz + (nz - 0.5f) * amp);
        }
    }

    /**
     * 3D value noise（Blender Noise Texture：Scale=2, Detail=2, Roughness=0.5）。
     * 输出范围约 [-1, 1]，低频平滑（2 个 octave）。
     */
    private static float valueNoise3D(float x, float y, float z, long seed) {
        float value = 0f;
        float amplitude = 0.5f;
        float frequency = 1f;
        for (int octave = 0; octave < 2; octave++) {
            value += amplitude * noise3D(x * frequency, y * frequency, z * frequency, seed + octave * 31);
            amplitude *= 0.5f;
            frequency *= 2.0f;
        }
        // 归一化到约 [-1, 1]（2 octave 累加约 0.75）
        return value / 0.75f;
    }

    /**
     * 3D value noise：哈希 + 平滑三线性插值，确定性（同 seed 同结果）。
     */
    private static float noise3D(float x, float y, float z, long seed) {
        int xi = floor(x);
        int yi = floor(y);
        int zi = floor(z);
        float xf = x - xi;
        float yf = y - yi;
        float zf = z - zi;

        float u = xf * xf * (3f - 2f * xf);
        float v = yf * yf * (3f - 2f * yf);
        float w = zf * zf * (3f - 2f * zf);

        float n000 = hash3D(xi, yi, zi, seed);
        float n100 = hash3D(xi + 1, yi, zi, seed);
        float n010 = hash3D(xi, yi + 1, zi, seed);
        float n110 = hash3D(xi + 1, yi + 1, zi, seed);
        float n001 = hash3D(xi, yi, zi + 1, seed);
        float n101 = hash3D(xi + 1, yi, zi + 1, seed);
        float n011 = hash3D(xi, yi + 1, zi + 1, seed);
        float n111 = hash3D(xi + 1, yi + 1, zi + 1, seed);

        float n00 = lerp(n000, n100, u);
        float n10 = lerp(n010, n110, u);
        float n01 = lerp(n001, n101, u);
        float n11 = lerp(n011, n111, u);
        float n0 = lerp(n00, n10, v);
        float n1 = lerp(n01, n11, v);
        return lerp(n0, n1, w);
    }

    /**
     * 哈希：整数坐标 → [0,1) 浮点，确定性。
     */
    private static float hash3D(int x, int y, int z, long seed) {
        long h = seed;
        h = h * 374761393L + x * 668265263L;
        h = h * 374761393L + y * 668265263L;
        h = h * 374761393L + z * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        return (float) ((h & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL);
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
