package org.academy.api.client.render.vfxgraph.arc;

import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.model.*;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M30 一比一复刻（2026-08-23，从实际 {@code 闪电附着.blend} 提取权威数据后）：
 * 验证每项 Blender 行为都被忠实复刻——逐点噪声乘数 pa 脉冲（端点 0）、噪声幅度对齐 Blender、
 * 漂移 = 场景秒（不乘游离速度）、实例随机跨度 0.4~1.2×、仿真区爬行游走、age 亮度先亮后灭、
 * 控制柄不含电弧粗细、接触弧半径走 FloatCurve.009。
 */
class M30FaithfulReplicationTest {
    private VfxBlockRegistry blocks;
    private VfxOperatorRegistry ops;
    private SimpleNodeRegistry metadata;

    @BeforeEach
    void setUp() {
        blocks = new VfxBlockRegistry();
        ops = new VfxOperatorRegistry();
        metadata = new SimpleNodeRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);
    }

    private static ArcCurve surfaceArc(long seed) {
        var arc = new ArcCurve();
        CurveGenerator.generateSurfaceArc(arc,
                0, 0, 0, 0, 1, 0,
                1.0f, 0.78f, 0.006f, 12,
                BlenderArcReference.ARC_COLOR_BLUE[0], BlenderArcReference.ARC_COLOR_BLUE[1],
                BlenderArcReference.ARC_COLOR_BLUE[2], 1f,
                BlenderArcReference.ARC_LIFETIME, seed);
        return arc;
    }

    /**
     * M30：逐点噪声乘数 pa = 脉冲曲线(spline因子) × Random[0.4..2.2] —— 端点 0、中段满幅。
     */
    @Test
    void noisePaIsPulseWithRandomScale() {
        for (long seed : new long[]{1, 42, 777, 99991}) {
            var arc = surfaceArc(seed);
            assertEquals(0f, arc.pa(0), 1e-6f, "pa at start endpoint = 0 (Blender Endpoint 排除噪声)");
            assertEquals(0f, arc.pa(arc.size() - 1), 1e-6f, "pa at end endpoint = 0");
            for (int i = 1; i < arc.size() - 1; i++) {
                float p = arc.pa(i);
                assertTrue(p >= 0f, "interior pa >= 0 at index " + i + ", got " + p);
                assertTrue(p <= 2.2f, "interior pa ≤ Random max 2.2, got " + p);
            }
            // 中段（spline 因子 ∈ [0.1,0.9]，脉冲满幅）必有 pa>0；近端点（<0.1/>0.9）归零
            int n = arc.size();
            boolean midActive = false;
            for (int i = 1; i < n - 1; i++) {
                float t = (float) i / (n - 1);
                if (t >= 0.1f && t <= 0.9f) midActive = midActive || arc.pa(i) > 0f;
            }
            assertTrue(midActive, "middle-band points should carry positive pa (pulse full-width)");
        }
    }

    /**
     * M30：噪声幅度对齐 Blender (noise−0.5)×pa×噪波强度(0.5) —— 中段位移明显（旧实现 0.036 弱 9 倍）。
     */
    @Test
    void noiseDisplacementMatchesBlenderMagnitude() {
        var arc = surfaceArc(42L);
        float[] before = new float[arc.size()];
        for (int i = 0; i < arc.size(); i++) before[i] = arc.x(i);
        NoiseAnimator.animate(arc, 1f, 1.5f, 0.5f, 2f, 42L);
        // 端点 pa=0 → 完全不动（复刻 Set Position.001 Selection=NOT(Endpoint)）
        assertEquals(before[0], arc.x(0), 1e-6f, "start endpoint unaffected by noise (pa=0)");
        assertEquals(before[arc.size() - 1], arc.x(arc.size() - 1), 1e-6f, "end endpoint unaffected by noise (pa=0)");
        // 中段：幅度 ±0.5×pa(0.4~2.2)×0.5 → 至少有一个点位移明显（>0.1，旧实现 ≤0.036）
        float maxDisp = 0f;
        for (int i = 1; i < arc.size() - 1; i++) {
            maxDisp = Math.max(maxDisp, Math.abs(arc.x(i) - before[i]));
        }
        assertTrue(maxDisp > 0.1f, "interior noise displacement should reach Blender magnitude, got " + maxDisp);
    }

    /**
     * M30：噪声漂移 = 场景秒×(1,1,1)，不乘游离速度（游离速度只驱动仿真区爬行）。
     */
    @Test
    void noiseDriftIndependentOfDriftSpeed() {
        var a = surfaceArc(9L);
        var b = surfaceArc(9L);
        NoiseAnimator.animate(a, 2f, 0.1f, 0.5f, 2f, 9L);
        NoiseAnimator.animate(b, 2f, 9.9f, 0.5f, 2f, 9L);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.x(i), b.x(i), 1e-6f, "driftSpeed must NOT affect noise drift (scene seconds only)");
            assertEquals(a.y(i), b.y(i), 1e-6f);
            assertEquals(a.z(i), b.z(i), 1e-6f);
        }
    }

    /**
     * M30：实例随机跨度缩放（Blender Instance Scale = Random[0.4..1.2]×电弧宽度）→ 弧大小各异。
     */
    @Test
    void spanVariesByInstanceRandomScale() {
        float spanMin = Float.MAX_VALUE, spanMax = -Float.MAX_VALUE;
        for (long seed : new long[]{1, 42, 777, 12345, 99991, 555}) {
            var arc = surfaceArc(seed);
            float dx = arc.x(0) - arc.x(arc.size() - 1);
            float dz = arc.z(0) - arc.z(arc.size() - 1);
            float span = (float) Math.sqrt(dx * dx + dz * dz);
            spanMin = Math.min(spanMin, span);
            spanMax = Math.max(spanMax, span);
            assertTrue(span >= 0.4f && span <= 1.2f, "span within [0.4,1.2]×height, got " + span);
        }
        assertTrue(spanMax - spanMin > 0.2f,
                "arcs should vary in size across seeds, observed [" + spanMin + "," + spanMax + "]");
    }

    /**
     * M30：仿真区爬行（Set Position）——弧基座每帧沿切平面滑移累积，端点仍被吸附回表面。
     */
    @Test
    void simCrawlAccumulatesWander() {
        var system = new VfxSystem("s",
                List.of(
                        new VfxContext("spawn", VfxContextType.SPAWN, "",
                                List.of(new VfxBlock("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "2",
                                                "probability", "1", "frequency", "0", "lifetime", "10"), List.of())), 0f, 0f),
                        new VfxContext("out", VfxContextType.OUTPUT, "",
                                List.of(new VfxBlock("bO", "vfx.block.output_arc", Map.of(), List.of())), 0f, 0f)),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));
        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        for (int i = 0; i < 5; i++) {
            sim.step(1f / 60f);
        }
        var buf = sim.arcBuffer();
        boolean sawWander = false;
        boolean sawSnap = false;
        for (int a = 0; a < buf.count(); a++) {
            var arc = buf.arc(a);
            if (!arc.hasArchBase()) continue;
            float w = Math.abs(arc.wanderX()) + Math.abs(arc.wanderY()) + Math.abs(arc.wanderZ());
            if (w > 1e-5f) sawWander = true;
            // 爬行后端点仍被 SurfaceConstraint 拉回平面
            if (Math.abs(arc.y(0)) < 1e-3f && Math.abs(arc.y(arc.size() - 1)) < 1e-3f) sawSnap = true;
        }
        assertTrue(sawWander, "surface arcs should accumulate crawl wander in sim");
        assertTrue(sawSnap, "endpoints should be re-snapped to surface after crawl");
    }

    /**
     * M30：age 亮度曲线先亮后灭（Float Curve.004 + 0.33 底），供渲染器烘焙闪烁。
     */
    @Test
    void lightCurveBrightensThenFades() {
        assertEquals(0.0f, BlenderArcCurves.sample(BlenderArcCurves.LIGHT, 0f), 1e-3f);
        assertEquals(0.9125f, BlenderArcCurves.sample(BlenderArcCurves.LIGHT, 0.5136f), 1e-3f, "peak mid-life");
        assertEquals(0.0f, BlenderArcCurves.sample(BlenderArcCurves.LIGHT, 1f), 1e-3f);
        // Light = 曲线×亮度 + 0.33×亮度（亮度=1）→ 出生 0.33，中段峰值 ≈1.24，临终 0.33
        assertEquals(0.33f, BlenderArcCurves.sample(BlenderArcCurves.LIGHT, 0f) + 0.33f, 1e-3f);
        assertTrue(BlenderArcCurves.sample(BlenderArcCurves.LIGHT, 0.5136f) + 0.33f > 1f, "mid-life light peaks above 1");
    }

    /**
     * M30：控制柄 = FloatCurve.001×Random×高度（**不含电弧粗细**）——curve 只缩放管半径。
     */
    @Test
    void archHeightIndependentOfCurveParam() {
        var a = new ArcCurve();
        var b = new ArcCurve();
        CurveGenerator.generateSurfaceArc(a, 0, 0, 0, 0, 1, 0, 1.0f, 0.2f, 0.006f, 12,
                1f, 1f, 1f, 1f, 20f, 55L);
        CurveGenerator.generateSurfaceArc(b, 0, 0, 0, 0, 1, 0, 1.0f, 0.9f, 0.006f, 12,
                1f, 1f, 1f, 1f, 20f, 55L);
        a.setAge(20f);
        b.setAge(20f);
        CurveGenerator.sampleSurfaceArch(a);
        CurveGenerator.sampleSurfaceArch(b);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.y(i), b.y(i), 1e-5f, "arch height must not depend on curve param");
        }
        // 但管半径仍受 width（= 有效半径基准）影响
        assertEquals(a.width(0), b.width(0), 1e-5f);
    }

    /**
     * M30：接触弧半径走 FloatCurve.009（生命系数）——出生满半径、临终归零。
     */
    @Test
    void contactRadiusUsesLifeCurve() {
        var born = new ArcCurve();
        CurveGenerator.generateContactArc(born, 0, 0, 0, 0, 2, 0, 0.008f, 12,
                1f, 1f, 1f, 1f, 6f, 3L);
        assertEquals(0.008f, born.width(0), 1e-4f, "contact radius at birth = radius × FloatCurve.009(0)=1");
        var dying = new ArcCurve();
        CurveGenerator.generateContactArc(dying, 0, 0, 0, 0, 2, 0, 0.008f, 12,
                1f, 1f, 1f, 1f, 6f, 3L);
        dying.setAge(6f);
        CurveGenerator.sampleSurfaceArch(dying);
        assertTrue(dying.width(0) < 1e-3f, "contact radius at death → 0 (FloatCurve.009(1)=0)");
        // 接触弧是直线（无拱）：所有点 y 随 0→2 直线插值，无 normal 起拱
        assertTrue(born.pa(born.size() / 2) > 0f, "contact noise pa pulse active in middle");
    }

    /**
     * M30：接触弧 pa 同样端点 0（Blender Set Position.005 Selection=NOT(Endpoint Selection.005)）。
     */
    @Test
    void contactNoiseEndpointsZero() {
        var arc = new ArcCurve();
        CurveGenerator.generateContactArc(arc, 0, 0, 0, 0, 2, 0, 0.008f, 12,
                1f, 1f, 1f, 1f, 6f, 3L);
        assertEquals(0f, arc.pa(0), 1e-6f);
        assertEquals(0f, arc.pa(arc.size() - 1), 1e-6f);
    }

    /**
     * M30：噪声强度/游离速度从 spawn 块属性接线到弧（修复此前接线断裂）。
     */
    @Test
    void spawnBlockWiresNoiseParamsToArc() {
        var system = new VfxSystem("s",
                List.of(
                        new VfxContext("spawn", VfxContextType.SPAWN, "",
                                List.of(new VfxBlock("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "2",
                                                "probability", "1", "frequency", "0", "lifetime", "5",
                                                "noise_strength", "0.5", "drift_speed", "1.5"), List.of())), 0f, 0f),
                        new VfxContext("out", VfxContextType.OUTPUT, "",
                                List.of(new VfxBlock("bO", "vfx.block.output_arc", Map.of(), List.of())), 0f, 0f)),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));
        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(1f / 60f);
        var buf = sim.arcBuffer();
        assertTrue(buf.count() > 0);
        var arc = buf.arc(0);
        assertTrue(arc.hasNoiseStrength(), "arc should carry noise_strength from spawn block");
        assertEquals(0.5f, arc.noiseStrength(), 1e-4f);
        assertEquals(1.5f, arc.driftSpeed(), 1e-4f);
    }
}
