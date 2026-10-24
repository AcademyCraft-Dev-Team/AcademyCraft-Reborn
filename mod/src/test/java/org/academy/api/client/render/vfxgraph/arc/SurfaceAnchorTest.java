package org.academy.api.client.render.vfxgraph.arc;

import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxFlowEdge;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks;
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry;
import org.academy.api.client.render.vfxgraph.operator.VfxOperators;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceAnchorTest {
    private VfxBlockRegistry blocks;
    private VfxOperatorRegistry ops;

    @BeforeEach
    void setUp() {
        blocks = new VfxBlockRegistry();
        ops = new VfxOperatorRegistry();
        var metadata = new SimpleNodeRegistry();
        VfxBlocks.registerAll(metadata, blocks);
        VfxOperators.registerAll(metadata, ops);
    }

    private static VfxBlock block(String id, String type, Map<String, String> props) {
        return new VfxBlock(id, type, props, List.of());
    }

    private static VfxContext ctx(String id, VfxContextType type, VfxBlock... blocks) {
        return new VfxContext(id, type, "", List.of(blocks), 0f, 0f);
    }

    private static VfxSystem system(String surface, String lifetime) {
        return new VfxSystem("anchored",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface", Map.of(
                                        "surface", surface,
                                        "density", "3",
                                        "probability", "1",
                                        "frequency", "0",
                                        "lifetime", lifetime,
                                        "noise_strength", "0"
                                ))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));
    }

    @Test
    void sampledArcsFollowMovingSurface() {
        var surface = new MovingSurface();
        var sim = new VfxSystemSimulator(system("test", "5"), blocks, ops, 42L, List.of());
        sim.setSurfaceSampler("test", surface);
        sim.step(1f / 60f);

        assertTrue(sim.arcBuffer().count() > 0);
        for (var i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            assertTrue(arc.hasAnchor());
            assertEquals(0f, arc.y(0), 1e-3f);
            assertEquals(0f, arc.y(arc.size() - 1), 1e-3f);
        }

        surface.height = 2f;
        sim.step(1f / 60f);

        for (var i = 0; i < sim.arcBuffer().count(); i++) {
            var arc = sim.arcBuffer().arc(i);
            if (!arc.hasAnchor()) continue;
            assertEquals(2f, arc.y(0), 1e-3f);
            assertEquals(2f, arc.y(arc.size() - 1), 1e-3f);
        }
    }

    @Test
    void missingSamplerEmitsNothing() {
        var sim = new VfxSystemSimulator(system("test", "5"), blocks, ops, 42L, List.of());
        sim.step(1f / 60f);
        assertEquals(0, sim.arcBuffer().count());
    }

    @Test
    void samplerFailureEmitsNothing() {
        var sampler = new SurfaceSampler() {
            @Override
            public boolean sample(int index, float u, float v, Vector3f position, Vector3f normal) {
                return false;
            }
        };
        var sim = new VfxSystemSimulator(system("test", "5"), blocks, ops, 42L, List.of());
        sim.setSurfaceSampler("test", sampler);
        sim.step(1f / 60f);
        assertEquals(0, sim.arcBuffer().count());
    }

    @Test
    void anchoredArcKeepsSurfaceReference() {
        var surface = new MovingSurface();
        var sim = new VfxSystemSimulator(system("test", "5"), blocks, ops, 42L, List.of());
        sim.setSurfaceSampler("test", surface);
        sim.step(1f / 60f);
        var arc = sim.arcBuffer().arc(0);
        assertTrue(arc.hasAnchor());
        assertFalse(arc.hasSurface());
        assertEquals(surface, arc.anchorSampler());
    }

    @Test
    void anchoredArcsEmitSparks() {
        var system = new VfxSystem("sparks",
                List.of(
                        ctx("spawn", VfxContextType.SPAWN,
                                block("bS", "vfx.block.arc_surface", Map.of(
                                        "surface", "test",
                                        "density", "3",
                                        "probability", "1",
                                        "frequency", "0",
                                        "lifetime", "5",
                                        "noise_strength", "0"
                                )),
                                block("bP", "vfx.block.arc_spark", Map.of(
                                        "probability", "1",
                                        "max_sparks", "3",
                                        "lifetime", "1"
                                ))),
                        ctx("out", VfxContextType.OUTPUT,
                                block("bO", "vfx.block.output_arc", Map.of()))
                ),
                List.of(), List.of(new VfxFlowEdge("spawn", "out")),
                List.of(), List.of(), List.of("bO"));
        var sim = new VfxSystemSimulator(system, blocks, ops, 42L, List.of());
        sim.setSurfaceSampler("test", new MovingSurface());
        sim.step(1f / 60f);

        var anchored = 0;
        for (var i = 0; i < sim.arcBuffer().count(); i++) {
            if (sim.arcBuffer().arc(i).hasAnchor()) anchored++;
        }
        assertTrue(anchored > 0);
        assertTrue(sim.arcBuffer().count() > anchored);
    }

    private static final class MovingSurface implements SurfaceSampler {
        private float height;

        @Override
        public int slotCount() {
            return 4;
        }

        @Override
        public boolean sample(int index, float u, float v, Vector3f position, Vector3f normal) {
            position.set(index * 0.1f, height, 0f);
            normal.set(0f, 1f, 0f);
            return true;
        }

        @Override
        public boolean nearestPoint(float x, float y, float z, Vector3f destination) {
            destination.set(x, height, z);
            return true;
        }
    }
}
