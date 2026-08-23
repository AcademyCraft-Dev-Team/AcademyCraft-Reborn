package org.academy.api.client.render.vfxgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphEffectTest {
    private VfxNodeRegistry vfxRegistry;

    @BeforeEach
    void setUp() {
        vfxRegistry = new VfxNodeRegistry();
        VfxNodes.registerAll(new SimpleNodeRegistry(), vfxRegistry);
    }

    private static GraphNode node(String id, String type, Map<String, String> props) {
        return new GraphNode(id, type, props, List.of(), 0f, 0f);
    }

    @Test
    void ticksAndSpawnsParticles() {
        var graph = new Graph("g",
                List.of(node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point"))),
                List.of(), List.of(), List.of());

        var effect = new GraphEffect(graph, vfxRegistry);
        effect.tick(0.1f);
        effect.tick(0.1f);

        assertEquals(2, effect.buffer().count());
    }

    @Test
    void parameterOverrideRebuildsSimulator() {
        var graph = new Graph("g",
                List.of(node("spawn", "vfx.spawn_rate", Map.of("rate", "10", "lifetime", "100", "shape", "point"))),
                List.of(), List.of(), List.of());

        var effect = new GraphEffect(graph, vfxRegistry);
        effect.tick(0.1f);
        assertEquals(1, effect.buffer().count());

        // 覆盖 rate=0 → 重建（状态重置），之后不再产粒
        effect.setParameter("spawn", "rate", "0");
        effect.tick(0.1f);
        effect.tick(0.1f);

        assertEquals(0, effect.buffer().count());
    }
}
