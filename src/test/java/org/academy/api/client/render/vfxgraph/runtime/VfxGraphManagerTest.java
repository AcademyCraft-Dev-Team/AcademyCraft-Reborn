package org.academy.api.client.render.vfxgraph.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.serialize.GraphSchemaVersion;
import org.academy.api.client.render.graph.serialize.JsonGraphCodec;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.model.VfxContext;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxFlowEdge;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VfxGraphManagerTest {
    private static final Identifier ASSET = Identifier.fromNamespaceAndPath("academy", "vfxgraph/test_burst");

    private JsonGraphCodec codec;

    @BeforeEach
    void setUp() {
        codec = new JsonGraphCodec(new SimpleNodeRegistry());
        var manager = VfxGraphManager.INSTANCE;
        manager.init();
        manager.clearEffects();
        manager.invalidateAll();
    }

    @AfterEach
    void tearDown() {
        VfxGraphManager.INSTANCE.close();
    }

    private Graph burstGraph() {
        return new Graph("test_burst",
                List.of(
                        new GraphNode("spawn", "vfx.spawn_rate",
                                Map.of("rate", "10", "lifetime", "100", "shape", "point"), List.of(), 0f, 0f),
                        new GraphNode("out", "vfx.output_quad", Map.of(), List.of(), 0f, 0f)
                ),
                List.of(), List.of(), List.of("out"));
    }

    private void registerAsset(Graph graph) {
        VfxGraphManager.INSTANCE.registerAsset(ASSET, codec.encode(graph));
    }

    @Test
    void spawnTicksAndProducesParticles() {
        registerAsset(burstGraph());
        var effect = VfxGraphManager.INSTANCE.spawn(ASSET, new Vector3f(1f, 2f, 3f));
        assertNotNull(effect);
        assertEquals(1, VfxGraphManager.INSTANCE.effectCount());
        assertEquals(1f, effect.position().x, 1e-5f);

        for (int i = 0; i < 5; i++) {
            VfxGraphManager.INSTANCE.tick(0.1f);
        }
        assertEquals(5, effect.effect().buffer().count());
    }

    @Test
    void spawnUnknownAssetThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> VfxGraphManager.INSTANCE.spawn(Identifier.fromNamespaceAndPath("academy", "vfxgraph/nope"),
                        new Vector3f()));
    }

    @Test
    void stopRemovesEffectOnNextTick() {
        registerAsset(burstGraph());
        var effect = VfxGraphManager.INSTANCE.spawn(ASSET, new Vector3f());
        VfxGraphManager.INSTANCE.stop(effect);
        VfxGraphManager.INSTANCE.tick(0.1f);
        assertEquals(0, VfxGraphManager.INSTANCE.effectCount());
    }

    @Test
    void clearEffectsRemovesAll() {
        registerAsset(burstGraph());
        VfxGraphManager.INSTANCE.spawn(ASSET, new Vector3f());
        VfxGraphManager.INSTANCE.spawn(ASSET, new Vector3f(5f, 0f, 0f));
        assertEquals(2, VfxGraphManager.INSTANCE.effectCount());
        VfxGraphManager.INSTANCE.clearEffects();
        assertEquals(0, VfxGraphManager.INSTANCE.effectCount());
    }

    @Test
    void reloadReplacesLiveEffectGraph() {
        registerAsset(burstGraph());
        var effect = VfxGraphManager.INSTANCE.spawn(ASSET, new Vector3f());
        for (int i = 0; i < 2; i++) {
            VfxGraphManager.INSTANCE.tick(0.1f);
        }
        assertEquals(2, effect.effect().buffer().count());

        // 重载：rate=0 → 重建模拟器，之后不再产粒
        var reloaded = new Graph("test_burst",
                List.of(
                        new GraphNode("spawn", "vfx.spawn_rate",
                                Map.of("rate", "0", "lifetime", "100", "shape", "point"), List.of(), 0f, 0f),
                        new GraphNode("out", "vfx.output_quad", Map.of(), List.of(), 0f, 0f)
                ),
                List.of(), List.of(), List.of("out"));
        registerAsset(reloaded);
        for (int i = 0; i < 2; i++) {
            VfxGraphManager.INSTANCE.tick(0.1f);
        }
        assertEquals(0, effect.effect().buffer().count());
    }

    @Test
    void particleCapFreezesSpawnAtLimit() {
        // Bug 修复回归：粒子上限生效——达到上限后不再 tick 产粒
        var capGraph = new Graph("capped",
                List.of(
                        new GraphNode("spawn", "vfx.spawn_rate",
                                Map.of("rate", "10", "lifetime", "1000", "shape", "point"), List.of(), 0f, 0f),
                        new GraphNode("out", "vfx.output_quad", Map.of(), List.of(), 0f, 0f)
                ),
                List.of(), List.of(), List.of("out"));
        var assetId = Identifier.fromNamespaceAndPath("academy", "vfxgraph/capped");
        VfxGraphManager.INSTANCE.registerAsset(assetId, codec.encode(capGraph));
        VfxGraphManager.INSTANCE.budget().setMaxParticlesPerEffect(5);

        var effect = VfxGraphManager.INSTANCE.spawn(assetId, new Vector3f());
        for (int i = 0; i < 50; i++) {
            VfxGraphManager.INSTANCE.tick(0.1f);
        }
        // 达到上限 5 后不再增长
        assertTrue(effect.effect().buffer().count() <= 5);
    }

    @Test
    void registerAssetWithVersionedJson() {
        var json = codec.encode(burstGraph());
        var wrapper = new JsonObject();
        wrapper.addProperty(GraphSchemaVersion.VERSION_FIELD, GraphSchemaVersion.CURRENT);
        json.entrySet().forEach(e -> wrapper.add(e.getKey(), e.getValue()));
        VfxGraphManager.INSTANCE.registerAsset(ASSET, wrapper);
        var graph = VfxGraphManager.INSTANCE.assets().get(ASSET.toString());
        assertNotNull(graph);
        assertTrue(graph.nodes().size() == 2);
    }

    /** M27 容器资产路径：kind:"vfx" 经 VfxGraphManager spawn 走容器执行器。 */
    @Test
    void spawnContainerAssetThroughManager() {
        var system = new VfxSystem("test_container",
                List.of(
                        new VfxContext("ctx_spawn", VfxContextType.SPAWN, "",
                                List.of(new VfxBlock("bS", "vfx.block.spawn_rate",
                                        Map.of("rate", "10", "lifetime", "100", "shape", "point"), List.of())), 0f, 0f),
                        new VfxContext("ctx_out", VfxContextType.OUTPUT, "",
                                List.of(new VfxBlock("bO", "vfx.block.output_quad", Map.of(), List.of())), 0f, 0f)
                ),
                List.of(),
                List.of(new VfxFlowEdge("ctx_spawn", "ctx_out")),
                List.of(),
                List.of(),
                List.of("bO"));

        var containerAsset = Identifier.fromNamespaceAndPath("academy", "vfxgraph/test_container");
        var json = new JsonVfxGraphCodec(new SimpleNodeRegistry()).encode(system);
        VfxGraphManager.INSTANCE.registerAsset(containerAsset, json);

        var effect = VfxGraphManager.INSTANCE.spawn(containerAsset, new Vector3f(1f, 2f, 3f));
        assertNotNull(effect);
        for (int i = 0; i < 5; i++) {
            VfxGraphManager.INSTANCE.tick(0.1f);
        }
        assertEquals(5, effect.effect().buffer().count());
    }
}
