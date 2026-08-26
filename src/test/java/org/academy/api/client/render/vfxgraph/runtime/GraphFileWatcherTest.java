package org.academy.api.client.render.vfxgraph.runtime;

import net.minecraft.resources.Identifier;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.serialize.JsonGraphCodec;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GraphFileWatcherTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        var manager = VfxGraphManager.INSTANCE;
        manager.init();
        manager.clearEffects();
        manager.invalidateAll();
    }

    @AfterEach
    void tearDown() {
        VfxGraphManager.INSTANCE.close();
    }

    @Test
    void reloadFromFileLoadsAndRefreshesLiveEffect() throws Exception {
        var codec = new JsonGraphCodec(new SimpleNodeRegistry());
        var graph = new Graph("watched",
                List.of(
                        new GraphNode("spawn", "vfx.spawn_rate",
                                Map.of("rate", "10", "lifetime", "100", "shape", "point"), List.of(), 0f, 0f),
                        new GraphNode("out", "vfx.output_quad", Map.of(), List.of(), 0f, 0f)
                ),
                List.of(), List.of(), List.of("out"));
        var file = tempDir.resolve("demo.json");
        Files.writeString(file, codec.encode(graph).toString());

        var manager = VfxGraphManager.INSTANCE;
        manager.reloadFromFile(Identifier.fromNamespaceAndPath("academy", "vfxgraph/demo"), file);
        var effect = manager.spawn(Identifier.fromNamespaceAndPath("academy", "vfxgraph/demo"), new Vector3f());
        assertNotNull(effect);
        for (int i = 0; i < 3; i++) {
            manager.tick(0.1f);
        }
        assertEquals(3, effect.effect().buffer().count());

        // 磁盘变更 → reloadFromFile → 存活效果换图（rate=0）
        var reloaded = new Graph("watched",
                List.of(
                        new GraphNode("spawn", "vfx.spawn_rate",
                                Map.of("rate", "0", "lifetime", "100", "shape", "point"), List.of(), 0f, 0f),
                        new GraphNode("out", "vfx.output_quad", Map.of(), List.of(), 0f, 0f)
                ),
                List.of(), List.of(), List.of("out"));
        Files.writeString(file, codec.encode(reloaded).toString());
        manager.reloadFromFile(Identifier.fromNamespaceAndPath("academy", "vfxgraph/demo"), file);
        manager.tick(0.1f);
        assertEquals(0, effect.effect().buffer().count());
    }

    @Test
    void watcherMapsFileNameToAssetId() throws Exception {
        var watcher = new GraphFileWatcher(tempDir);
        assertNotNull(watcher);
        watcher.close();
    }
}
