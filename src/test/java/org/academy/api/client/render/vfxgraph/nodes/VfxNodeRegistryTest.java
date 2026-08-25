package org.academy.api.client.render.vfxgraph.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VfxNodeRegistryTest {
    private VfxNodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new VfxNodeRegistry();
        VfxNodes.registerAll(new SimpleNodeRegistry(), registry);
    }

    @Test
    void registerAndFind() {
        assertNull(registry.find("missing"));
        assertEquals(47, catalogSize());
    }

    @Test
    void spawnRateFactoryProducesWorkingNode() {
        var factory = registry.find("vfx.spawn_rate");
        var node = new GraphNode("n", "vfx.spawn_rate",
                Map.of("rate", "10", "lifetime", "100", "shape", "point"), List.of(), 0f, 0f);
        SimNode sim = factory.create(node);
        assertEquals(true, sim != null);
    }

    @Test
    void allParamNodesRegistered() {
        for (String id : new String[]{
                "vfx.param_float", "vfx.param_vec3", "vfx.param_color",
                "vfx.param_curve", "vfx.param_gradient"
        }) {
            assertEquals(true, registry.find(id) != null, id + " should be registered");
        }
    }

    private int catalogSize() {
        // 简单统计：VfxNodes.registerAll 注册到 metadata（SimpleNodeRegistry）的节点类型数
        var metadata = new SimpleNodeRegistry();
        VfxNodes.registerAll(metadata, registry);
        return metadata.all().size();
    }
}
