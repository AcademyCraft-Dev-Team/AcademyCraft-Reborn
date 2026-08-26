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
 * M29：arc_spark 块（Blender 粒子火花：弧→点 + 溅射 + 迷你管）容器端到端单测。
 */
class SparkArcBlockTest {
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

    private static VfxBlock block(String id, String type, Map<String, String> props) {
        return new VfxBlock(id, type, props, List.of());
    }

    private static VfxContext ctx(String id, VfxContextType type, VfxBlock... blocks) {
        return new VfxContext(id, type, "", List.of(blocks), 0f, 0f);
    }

    /**
     * spark 依赖上游 arc_surface 提供带表面弧，自身产生迷你火花弧。
     */
    private static VfxSystem sparkSystem(Map<String, String> sparkProps) {
        return new VfxSystem("sp",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "1",
                                                "probability", "1", "frequency", "0", "lifetime", "3")),
                                block("bK", "vfx.block.arc_spark", sparkProps)),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));
    }

    @Test
    void sparkBlockSpawnsMiniArcs() {
        var system = sparkSystem(Map.of("probability", "1", "splash_speed", "1.3", "lifetime", "0.5"));
        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(1f / 60f);

        var buf = sim.arcBuffer();
        // 表面弧 + 火花弧都入同一个 buffer；火花弧是无表面迷你 2 点弧
        boolean hasSpark = false;
        for (int a = 0; a < buf.count(); a++) {
            var arc = buf.arc(a);
            if (!arc.hasSurface()) {
                hasSpark = true;
                assertTrue(arc.size() >= 2, "spark should be a mini arc with >= 2 points");
                assertEquals(0.5f, arc.lifetime(), 1e-4f, "spark lifetime from props");
            }
        }
        assertTrue(hasSpark, "arc_spark should produce mini spark arcs (no surface)");
    }

    @Test
    void sparkProbabilityZeroProducesNoSparks() {
        var system = sparkSystem(Map.of("probability", "0", "splash_speed", "1.3", "lifetime", "0.5"));
        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(1f / 60f);

        var buf = sim.arcBuffer();
        for (int a = 0; a < buf.count(); a++) {
            assertTrue(buf.arc(a).hasSurface(), "probability 0 → only surface arcs, no sparks");
        }
    }

    @Test
    void sparkDirectionExtendsFromSource() {
        var system = sparkSystem(Map.of("probability", "1", "splash_speed", "2.0", "lifetime", "0.5"));
        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(1f / 60f);

        var buf = sim.arcBuffer();
        for (int a = 0; a < buf.count(); a++) {
            var arc = buf.arc(a);
            if (!arc.hasSurface()) {
                // 火花弧：迷你管（Blender Curve Line 长度 = 实例 Scale 0.01~0.03 × 粒子缩放），
                // 起点在源弧控制点，终点沿溅射方向延伸
                float dx = arc.x(1) - arc.x(0);
                float dy = arc.y(1) - arc.y(0);
                float dz = arc.z(1) - arc.z(0);
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                assertTrue(len > 1e-4f, "spark should extend, len=" + len);
                assertTrue(len < 0.5f, "spark is a mini tube (Blender scale 0.01~0.03 × particle scale)");
            }
        }
    }
}
