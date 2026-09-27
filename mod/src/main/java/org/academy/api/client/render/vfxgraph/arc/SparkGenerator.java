package org.academy.api.client.render.vfxgraph.arc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 火花粒子生成器（M22-Rev2）：复刻 Blender 粒子流（Curve to Points → Delete → Instance）。
 *
 * <p>从 ArcCurve 的存活端点提取火花种子，随机保留后生成小弧线（火花）。
 * Blender 对应：Curve to Points → Delete Geometry(random 40% cull) →
 * Instance on Points(Curve Line) → Realize → Curve to Mesh(Circle r=0.005)。</p>
 */
public final class SparkGenerator {
    private SparkGenerator() {
    }

    /**
     * 从 ArcCurve 生成火花弧线。
     *
     * @param arc          源弧线（主弧）
     * @param survivalRate 火花存活概率（默认 0.4，即保留 40%）
     * @param sparkRadius  火花管半径（默认 0.005）
     * @param sparkLength  火花长度（默认 0.05）
     * @param lifetime     火花生命周期
     * @param seed         随机种子
     * @return 火花弧线列表（每个火花 = 一条小 ArcCurve）
     */
    public static List<SparkData> generate(ArcCurve arc, float survivalRate,
                                           float sparkRadius, float sparkLength,
                                           float lifetime, long seed) {
        var random = new Random(seed);
        var sparks = new ArrayList<SparkData>();

        // 提取端点（每个分支的最后一个点）
        var endpoints = extractEndpoints(arc);

        for (var ep : endpoints) {
            // 随机删除（Blender: Delete Geometry with probability）
            if (random.nextFloat() > survivalRate) continue;

            // 火花方向：法线方向 + 随机扰动
            var nx = ep.nx;
            var ny = ep.ny;
            var nz = ep.nz;
            var dir = SurfaceDistributor.tangentDirection(nx, ny, nz, (float) Math.PI / 3, random);

            // 火花起点（端点位置）
            var sx = ep.x;
            var sy = ep.y;
            var sz = ep.z;

            // 火花终点（沿方向延伸）
            var ex = sx + dir[0] * sparkLength;
            var ey = sy + dir[1] * sparkLength;
            var ez = sz + dir[2] * sparkLength;

            // 火花生命周期（随机变化 ±30%）
            var sparkLife = lifetime * (0.7f + 0.6f * random.nextFloat());

            sparks.add(new SparkData(sx, sy, sz, ex, ey, ez, sparkRadius, sparkLife,
                    ep.r, ep.g, ep.b, ep.a));
        }
        return sparks;
    }

    /**
     * 提取弧线端点（每个分支最后一个控制点）。
     */
    private static List<Endpoint> extractEndpoints(ArcCurve arc) {
        var endpoints = new ArrayList<Endpoint>();
        if (arc.size() < 2) return endpoints;

        // 简单策略：取每段 generation 的最后一个点
        float lastGen = -1;
        for (var i = 0; i < arc.size(); i++) {
            var gen = arc.generation(i);
            if (gen != lastGen) {
                // 新分支开始，前一个点是上一个分支的端点
                if (i > 0) {
                    addEndpoint(endpoints, arc, i - 1);
                }
                lastGen = gen;
            }
        }
        // 最后一个分支的端点
        addEndpoint(endpoints, arc, arc.size() - 1);
        return endpoints;
    }

    private static void addEndpoint(List<Endpoint> endpoints, ArcCurve arc, int idx) {
        // 用相邻点估算法线
        var prev = Math.max(0, idx - 1);
        var next = Math.min(arc.size() - 1, idx + 1);
        var tx = arc.x(next) - arc.x(prev);
        var ty = arc.y(next) - arc.y(prev);
        var tz = arc.z(next) - arc.z(prev);
        var tlen = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        float nx, ny, nz;
        if (tlen < 1e-6f) {
            nx = 0;
            ny = 1;
            nz = 0;
        } else {
            nx = tx / tlen;
            ny = ty / tlen;
            nz = tz / tlen;
        }
        endpoints.add(new Endpoint(arc.x(idx), arc.y(idx), arc.z(idx), nx, ny, nz,
                arc.r(), arc.g(), arc.b(), arc.a()));
    }

    /**
     * 火花数据。
     */
    public record SparkData(
            float startX, float startY, float startZ,
            float endX, float endY, float endZ,
            float radius, float lifetime,
            float r, float g, float b, float a
    ) {
        /**
         * 转为 ArcCurve（供 CurveToMeshBuilder 使用）。
         */
        public ArcCurve toArcCurve() {
            var arc = new ArcCurve();
            arc.addPoint(startX, startY, startZ, radius, 0);
            arc.addPoint(endX, endY, endZ, radius * 0.3f, 0); // 尖端收窄
            arc.setColor(r, g, b, a);
            arc.setLifetime(lifetime);
            return arc;
        }
    }

    /**
     * 端点数据。
     */
    private record Endpoint(float x, float y, float z, float nx, float ny, float nz,
                            float r, float g, float b, float a) {
    }
}
