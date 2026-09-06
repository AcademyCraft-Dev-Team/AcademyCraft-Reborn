package org.academy.api.client.render.vfxgraph.arc;

import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxFlowEdge;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * arc_bolt 步进重掷（reshuffle）：寿命内按固定节奏替换为全新 seed 的锯齿构型，
 * 产生闪电式跳动运动感。验证：段内始终恰好一条弧 + 相邻重掷几何明显不同 + 锯齿离开固定平面。
 */
class ArcBoltReshuffleTest {
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

    private VfxSystem system(Map<String, String> props) {
        return new VfxSystem("bolt",
                List.of(
                        new VfxContext("spawn", VfxContextType.SPAWN, "",
                                List.of(new VfxBlock("bB", "vfx.block.arc_bolt", props, List.of())), 0f, 0f),
                        new VfxContext("out", VfxContextType.OUTPUT, "",
                                List.of(new VfxBlock("bO", "vfx.block.output_arc", Map.of(), List.of())), 0f, 0f)
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "out")),
                List.of(),
                List.of(),
                List.of("bO"));
    }

    @Test
    void reshuffleProducesDistinctGeometryOutOfFixedPlane() {
        var sim = new VfxSystemSimulator(system(Map.ofEntries(
                Map.entry("from_x", "0"), Map.entry("from_y", "0"), Map.entry("from_z", "0"),
                Map.entry("to_x", "0"), Map.entry("to_y", "1"), Map.entry("to_z", "0"),
                Map.entry("probability", "1"), Map.entry("reshuffle", "0.1"), Map.entry("lifetime", "2"),
                Map.entry("branch_depth", "0"), Map.entry("segments", "8"), Map.entry("width", "0.01"),
                Map.entry("interval", "0"))), blocks, ops, 42L, List.of());

        java.util.List<float[]> snapshots = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            sim.step(0.1f);
            var buf = sim.arcBuffer();
            assertEquals(1, buf.count(), "always exactly one live bolt during burst");
            var arc = buf.arc(0);
            var pts = new float[arc.size() * 3];
            for (int k = 0; k < arc.size(); k++) {
                pts[k * 3] = arc.x(k);
                pts[k * 3 + 1] = arc.y(k);
                pts[k * 3 + 2] = arc.z(k);
            }
            snapshots.add(pts);
        }

        float maxDelta = 0f;
        for (int i = 1; i < snapshots.size(); i++) {
            var a = snapshots.get(i - 1);
            var b = snapshots.get(i);
            assertEquals(a.length, b.length);
            for (int k = 0; k < a.length; k++) {
                maxDelta = Math.max(maxDelta, Math.abs(a[k] - b[k]));
            }
        }
        // 相邻重掷应产生明显不同的锯齿构型
        assertTrue(maxDelta > 1e-3f, "geometry should change between re-rolls, got " + maxDelta);

        // 竖直闪电的锯齿应离开固定 X-Y 平面（每 seed 旋转锯齿平面 → 整体朝向变化 = 可感知的跳动）
        float maxZ = 0f;
        for (var pts : snapshots) {
            for (int k = 2; k < pts.length; k += 3) maxZ = Math.max(maxZ, Math.abs(pts[k]));
        }
        assertTrue(maxZ > 0.02f, "bolt should leave the fixed plane across re-rolls, maxZ=" + maxZ);
    }

    @Test
    void differentSeedsProduceDifferentBoltGeometry() {
        var a = new ArcCurve();
        var b = new ArcCurve();
        CurveGenerator.generateFromTo(
                a, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0.01f, 8, 1, 1, 1, 1, 1f, 1L,
                0, 0, 1.57f, 0.3f, 0.35f, 0.6f);
        CurveGenerator.generateFromTo(
                b, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0.01f, 8, 1, 1, 1, 1, 1f, 2L,
                0, 0, 1.57f, 0.3f, 0.35f, 0.6f);
        float maxDelta = 0f;
        for (int k = 0; k < Math.min(a.size(), b.size()); k++) {
            maxDelta = Math.max(maxDelta, Math.abs(a.x(k) - b.x(k)));
            maxDelta = Math.max(maxDelta, Math.abs(a.y(k) - b.y(k)));
            maxDelta = Math.max(maxDelta, Math.abs(a.z(k) - b.z(k)));
        }
        assertTrue(maxDelta > 1e-3f, "seeds 1 vs 2 should differ, got " + maxDelta);
    }

    /**
     * branch_length_scale_param（技能经 SpawnVfxGraphPacket.floatParams 绑定存活参数）：
     * 更小的存活参数值应产生更短的分叉（弧长被整体放大时技能可借此抑制分叉过长）。
     */
    @Test
    void liveParamOverridesBranchLengthScale() {
        var param = new GraphParameter("branch_length_scale", "Branch Length Scale",
                ValueType.FLOAT, Value.of(1f), Optional.empty());
        var params = List.of(param);
        var props = Map.ofEntries(
                Map.entry("from_x", "0"), Map.entry("from_y", "0"), Map.entry("from_z", "0"),
                Map.entry("to_x", "0"), Map.entry("to_y", "1"), Map.entry("to_z", "0"),
                Map.entry("probability", "1"), Map.entry("lifetime", "1"), Map.entry("interval", "0"),
                Map.entry("branch_depth", "1"), Map.entry("branch_count", "2"),
                Map.entry("branch_length_scale", "1.0"), Map.entry("branch_length_scale_param", "branch_length_scale"),
                Map.entry("segments", "8"), Map.entry("width", "0.01"));
        var longSim = new VfxSystemSimulator(systemWithParams(props, params), blocks, ops, 42L, params);
        longSim.step(1f / 60f);
        var longArc = longSim.arcBuffer().arc(0);

        var shortSim = new VfxSystemSimulator(systemWithParams(props, params), blocks, ops, 42L, params);
        shortSim.setLiveParam("branch_length_scale", Value.of(0.05f));
        shortSim.step(1f / 60f);
        var shortArc = shortSim.arcBuffer().arc(0);

        var longMax = maxDistanceFromAxis(longArc);
        var shortMax = maxDistanceFromAxis(shortArc);
        assertTrue(shortMax < longMax,
                "smaller live branch_length_scale should shorten branches: short=" + shortMax + " long=" + longMax);
    }

    private VfxSystem systemWithParams(Map<String, String> props, List<GraphParameter> params) {
        return new VfxSystem("bolt",
                List.of(
                        new VfxContext("spawn", VfxContextType.SPAWN, "",
                                List.of(new VfxBlock("bB", "vfx.block.arc_bolt", props, List.of())), 0f, 0f),
                        new VfxContext("out", VfxContextType.OUTPUT, "",
                                List.of(new VfxBlock("bO", "vfx.block.output_arc", Map.of(), List.of())), 0f, 0f)
                ),
                List.of(),
                List.of(new VfxFlowEdge("spawn", "out")),
                List.of(),
                params,
                List.of("bO"));
    }

    private static float maxDistanceFromAxis(ArcCurve arc) {
        // 主轴 from=(0,0,0) → to=(0,1,0)；距主轴的垂直距离（x/z 平面）
        var max = 0f;
        for (var i = 0; i < arc.size(); i++) {
            var d = (float) Math.sqrt(arc.x(i) * arc.x(i) + arc.z(i) * arc.z(i));
            max = Math.max(max, d);
        }
        return max;
    }
}