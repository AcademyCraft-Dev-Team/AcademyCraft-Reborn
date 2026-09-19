package org.academy.api.client.render.vfxgraph.runtime;

import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry;
import org.academy.api.client.render.vfxgraph.nodes.VfxNodes;
import org.academy.api.client.render.vfxgraph.render.WorldTransform;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActiveEffectTest {
    @Test
    void proceduralPathBindingSurvivesContainerReloadAndHonorsEmptyInput() throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry();
        org.academy.api.client.render.vfxgraph.nodes.VfxBlocks.registerAll(metadata, blocks);
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/magnetic_weapon.json")) {
            var system = new org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec(metadata).decode(
                    com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                            java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            var active = new ActiveEffect("magnetic", system, vfxRegistry, blocks,
                    new org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry(), new Vector3f());
            boolean[] visible = {true};
            var path = new org.academy.api.common.arc.ArcPath(new org.academy.api.common.arc.path.LinePath(
                    new Vector3f(), new Vector3f(0, 2, 0)), List.of(), 4, List.of());
            active.bindArcs("paths", _ -> visible[0] ? List.of(path) : List.of());
            active.tick(0.05f);
            assertEquals(2, active.effect().arcBuffer().count());
            active.reload(system);
            active.tick(0.05f);
            assertEquals(2, active.effect().arcBuffer().count());
            visible[0] = false;
            active.tick(0.05f);
            assertEquals(0, active.effect().arcBuffer().count());
        }
    }

    @Test
    void sampledSurfaceSurvivesReloadAndMissingSurfaceHidesPatches() throws Exception {
        var metadata = new SimpleNodeRegistry();
        var blocks = new org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry();
        org.academy.api.client.render.vfxgraph.nodes.VfxBlocks.registerAll(metadata, blocks);
        try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/darkmatter_repair.json")) {
            var system = new org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec(metadata).decode(
                    com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                            java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            var active = new ActiveEffect("surface", system, vfxRegistry, blocks,
                    new org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry(), new Vector3f());
            boolean[] available = {true};
            active.bind("progress", () -> Value.of(0.5f));
            active.bindSurfaceSampler("surface", (i, u, v, p, n) -> { p.set(3, 2, 1); n.set(1, 0, 0); return available[0]; });
            active.tick(0.05f);
            assertTrue(active.effect().buffer().positionX(0) > 3);
            active.reload(system);
            active.tick(0.05f);
            assertTrue(active.effect().buffer().positionX(0) > 3);
            assertTrue(active.effect().buffer().alpha(0) > 0);
            available[0] = false;
            active.tick(0.05f);
            assertEquals(0, active.effect().buffer().alpha(0));
        }
    }

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
    void gameLifetimePausesAndAgesWhileHiddenWithoutDependingOnSimulationBudget() {
        var effect = new ActiveEffect("k", burstGraph("0"), vfxRegistry, new Vector3f());
        effect.setGameTimeLifetimeSeconds(0.5f);
        effect.setFrameVisible(false);
        assertFalse(effect.updateFrame(0.2f, null, 0));
        for (int i = 0; i < 100; i++) assertFalse(effect.updateFrame(0, null, 0));
        assertEquals(0.2f, effect.gameAgeSeconds(), 0.0001f);
        effect.reload(burstGraph("0"));
        assertTrue(effect.updateFrame(0.3f, null, 0), "Reloading or culling must not reset the game lifetime");
    }

    @Test
    void frameAttachmentUpdatesTransformBeforeCullingAndCanEndTheEffectAfterReload() {
        var effect = new ActiveEffect("k", burstGraph("0"), vfxRegistry, new Vector3f());
        var camera = new GraphCamera(new Vector3f(10, 5, 2), new Matrix4f(), new Matrix4f());
        boolean[] alive = {true};
        effect.bindFrame((active, frameCamera, partial) -> {
            active.setTransform(new Matrix4f().translation(frameCamera.position()).translate(partial, -1, -2)
                    .rotateY(0.8f).scale(0.75f));
            active.setCullingSphere(active.position(), 1);
            return alive[0];
        });
        effect.reload(burstGraph("0"));
        assertFalse(effect.updateFrame(0, camera, 0.5f));
        assertEquals(10.5f, effect.position().x);
        assertEquals(4, effect.cullingCenter().y);
        assertEquals(0.75f, effect.scale(), 0.0001f);
        alive[0] = false;
        assertTrue(effect.updateFrame(0, camera, 0.75f));
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
