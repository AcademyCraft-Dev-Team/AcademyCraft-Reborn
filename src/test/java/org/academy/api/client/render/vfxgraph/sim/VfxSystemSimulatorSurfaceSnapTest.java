package org.academy.api.client.render.vfxgraph.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.academy.api.client.render.vfxgraph.arc.SurfaceConstraint;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxFlowEdge;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.shape.MeshAssets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** M29：容器执行器端点表面吸附接线单测（SurfaceConstraint 接入 VfxSystemSimulator.step）。 */
class VfxSystemSimulatorSurfaceSnapTest {
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

    @Test
    void stepAppliesSurfaceConstraintToArcsWithSurface() {
        var system = new VfxSystem("s",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "1",
                                                "probability", "1", "frequency", "0", "lifetime", "5"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        // 多帧：噪声动画会让点漂移，但带表面的弧端点应被拉回平面 y=0
        for (int i = 0; i < 30; i++) {
            sim.step(1f / 60f);
        }
        var buf = sim.arcBuffer();
        for (int a = 0; a < buf.count(); a++) {
            var arc = buf.arc(a);
            if (!arc.hasSurface()) continue;
            assertEquals(0f, arc.y(0), 1e-3f, "endpoint should stay snapped to plane across frames");
            assertEquals(0f, arc.y(arc.size() - 1), 1e-3f);
        }
    }

    @Test
    void arcsWithoutSurfaceStayFree() {
        // 手动构造无表面弧：噪声动画应让其漂移（不被吸附）
        var surface = MeshAssets.plane(2f);
        var buf = new ArcBuffer();
        var arc = buf.add();
        arc.setLifetime(10f);
        arc.setSurface(surface); // 有表面
        for (int i = 0; i < 12; i++) {
            arc.addPoint(0, i * 0.1f, 0, 0.01f, 0);
        }
        float origY0 = arc.y(0);
        arc.setSurface(null); // 去掉表面 → 自由弧

        // 用 SurfaceConstraint 无表面时不应改变端点
        new SurfaceConstraint().constrain(arc);
        assertEquals(origY0, arc.y(0), 0f, "arc without surface should not be constrained");
    }

    @Test
    void constraintMovesOffPlaneEndpoint() {
        // 自由点远离平面 → 约束后应被拉回平面
        var arc = new org.academy.api.client.render.vfxgraph.arc.ArcCurve();
        arc.setLifetime(10f);
        arc.setSurface(MeshAssets.plane(2f));
        arc.addPoint(0, 5, 0, 0.01f, 0);
        arc.addPoint(0, 6, 0, 0.01f, 0);
        new SurfaceConstraint().constrain(arc);
        assertEquals(0f, arc.y(0), 1e-3f, "endpoint above plane should snap to y=0");
        assertEquals(0f, arc.y(1), 1e-3f, "endpoint above plane should snap to y=0");
        assertNotEquals(5f, arc.y(0), 1e-3f);
    }
}