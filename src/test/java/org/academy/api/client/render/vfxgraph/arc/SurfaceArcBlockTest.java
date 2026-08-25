package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxFlowEdge;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** M29：arc_surface 块（Blender 表面电弧）容器端到端单测。 */
class SurfaceArcBlockTest {
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
    void surfaceBlockSpawnsSurfaceArcs() {
        var system = new VfxSystem("s",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "2",
                                                "probability", "1", "frequency", "0", "lifetime", "1"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.step(1f / 60f);

        assertTrue(sim.arcBuffer().count() > 0, "arc_surface should spawn arcs on plane");
        var arc = sim.arcBuffer().arc(0);
        assertTrue(arc.size() >= 3, "surface arc should have control points");
        assertTrue(arc.hasSurface(), "surface arc should carry its surface for endpoint snap");
        // 表面点在平面上（y=0 附近）
        for (int i = 0; i < arc.size(); i++) {
            assertTrue(Math.abs(arc.y(i)) < 1.2f, "surface arc y should stay near plane: " + arc.y(i));
        }
    }

    @Test
    void surfaceArcEndpointsSnapToSurface() {
        var system = new VfxSystem("s",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "2",
                                                "probability", "1", "frequency", "0", "lifetime", "5"))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));

        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        // 跑多帧：端点应被 SurfaceConstraint 拉回平面（y→0）
        for (int i = 0; i < 10; i++) {
            sim.step(1f / 60f);
        }
        var buf = sim.arcBuffer();
        for (int a = 0; a < buf.count(); a++) {
            var arc = buf.arc(a);
            if (!arc.hasSurface()) continue;
            assertEquals(0f, arc.y(0), 1e-3f, "first endpoint should snap to plane (y=0)");
            assertEquals(0f, arc.y(arc.size() - 1), 1e-3f, "last endpoint should snap to plane (y=0)");
        }
    }

    @Test
    void surfaceArcDeterministicPerSeed() {
        var systemA = new VfxSystem("a",
                List.of(ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "2",
                                                "probability", "1", "frequency", "0", "lifetime", "1"))),
                        ctx("out", VfxContextType.OUTPUT, block("bO", "vfx.block.output_arc", Map.of()))),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")), List.of(), List.of(), List.of("bO"));
        var systemB = new VfxSystem("b",
                List.of(ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface",
                                        Map.of("mesh", "builtin:plane", "density", "2",
                                                "probability", "1", "frequency", "0", "lifetime", "1"))),
                        ctx("out", VfxContextType.OUTPUT, block("bO", "vfx.block.output_arc", Map.of()))),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")), List.of(), List.of(), List.of("bO"));

        var simA = new VfxSystemSimulator(systemA, blocks, ops, 99L, List.of());
        var simB = new VfxSystemSimulator(systemB, blocks, ops, 99L, List.of());
        simA.step(1f / 60f);
        simB.step(1f / 60f);
        assertEquals(simA.arcBuffer().count(), simB.arcBuffer().count());
        for (int i = 0; i < simA.arcBuffer().count(); i++) {
            assertEquals(simA.arcBuffer().arc(i).size(), simB.arcBuffer().arc(i).size());
        }
    }
}