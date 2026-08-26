package org.academy.api.client.render.vfxgraph.runtime;

import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes;
import org.academy.api.client.render.vfxgraph.render.WorldTransform;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActiveEffectTest {
    private VfxNodeRegistry vfxRegistry;

    @BeforeEach
    void setUp() {
        vfxRegistry = new VfxNodeRegistry();
        VfxNodes.registerAll(new SimpleNodeRegistry(), vfxRegistry);
    }

    private static Graph burstGraph(String rate) {
        return new Graph("g",
                List.of(
                        new GraphNode("spawn", "vfx.spawn_rate",
                                Map.of("rate", rate, "lifetime", "100", "shape", "point"), List.of(), 0f, 0f),
                        new GraphNode("out", "vfx.output_quad", Map.of(), List.of(), 0f, 0f)
                ),
                List.of(), List.of(), List.of("out"));
    }

    @Test
    void tickSpawnsAndIntegrates() {
        var effect = new ActiveEffect("k", burstGraph("10"), vfxRegistry, new Vector3f(0f, 0f, 0f));
        for (var i = 0; i < 3; i++) {
            assertFalse(effect.tick(0.1f));
        }
        assertEquals(3, effect.effect().buffer().count());
    }

    @Test
    void stopMarksForRemoval() {
        var effect = new ActiveEffect("k", burstGraph("10"), vfxRegistry, new Vector3f());
        effect.stop();
        assertTrue(effect.tick(0.1f));
    }

    @Test
    void worldTransformCombinesPositionRotationScale() {
        var effect = new ActiveEffect("k", burstGraph("0"), vfxRegistry, new Vector3f(1f, 2f, 3f));
        effect.setRotation(new Quaternionf().rotateY((float) Math.PI / 2f));
        effect.setScale(2f);

        var out = new float[3];
        effect.worldTransform().apply(1f, 0f, 0f, out);
        // local (1,0,0) → scale(2) → rot Y90 → (0,0,-2) → +pos(1,2,3) = (1,2,1)
        assertEquals(1f, out[0], 1e-4f);
        assertEquals(2f, out[1], 1e-4f);
        assertEquals(1f, out[2], 1e-4f);
    }

    @Test
    void bindInjectsLiveParamWithoutRebuild() {
        var graph = new Graph("g",
                List.of(
                        new GraphNode("spawn", "vfx.spawn_rate",
                                Map.of("rate", "10", "lifetime", "100", "shape", "point"), List.of(), 0f, 0f),
                        new GraphNode("vel", "vfx.init_velocity",
                                Map.of("vx", "0", "vy", "1", "vz", "0", "random", "0", "param", "dir"),
                                List.of(), 0f, 0f),
                        new GraphNode("integ", "vfx.update_velocity", Map.of(), List.of(), 0f, 0f)
                ),
                List.of(), List.of(), List.of("spawn"));
        var effect = new ActiveEffect("k", graph, vfxRegistry, new Vector3f());

        effect.bind("dir", () -> Value.of(new Vector3f(0f, 5f, 0f)));
        effect.tick(0.1f);
        assertEquals(0.5f, effect.effect().buffer().positionY(0), 1e-5f);
    }

    @Test
    void reloadKeepsTransformAndBindings() {
        var effect = new ActiveEffect("k", burstGraph("10"), vfxRegistry, new Vector3f(5f, 0f, 0f));
        effect.bind("p", () -> Value.of(1f));
        effect.tick(0.1f);

        // 重载 → 换图，位置/绑定保留
        effect.reload(burstGraph("0"));
        assertEquals(5f, effect.position().x, 1e-5f);
        assertInstanceOf(WorldTransform.class, effect.worldTransform());
        for (var i = 0; i < 2; i++) {
            effect.tick(0.1f);
        }
        assertEquals(0, effect.effect().buffer().count());
    }
}
