package org.academy.api.client.render.graph.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry;
import org.academy.api.client.render.graph.serialize.GraphMigration;
import org.academy.api.client.render.graph.serialize.GraphSchemaVersion;
import org.academy.api.client.render.graph.serialize.JsonGraphCodec;
import org.junit.jupiter.api.Test;

class GraphAssetsTest {
    private static final String GRAPH_JSON = """
            {"version":1,"id":"g","nodes":[],"edges":[],"parameters":[],"outputs":[]}
            """;

    @Test
    void loadsAndCachesByKey() {
        var assets = new GraphAssets(new JsonGraphCodec(new SimpleNodeRegistry()));

        var graph = assets.load("g", GRAPH_JSON);

        assertNotNull(graph);
        assertEquals("g", graph.id());
        assertTrue(assets.contains("g"));
        assertEquals(graph, assets.get("g"));
        assertEquals(1, assets.size());
    }

    @Test
    void invalidateRemovesFromCache() {
        var assets = new GraphAssets(new JsonGraphCodec(new SimpleNodeRegistry()));
        assets.load("g", GRAPH_JSON);

        assets.invalidate("g");

        assertNull(assets.get("g"));
        assertFalse(assets.contains("g"));
    }

    @Test
    void appliesMigrationsThroughLoader() {
        var migration = new GraphMigration() {
            @Override
            public int fromVersion() {
                return 0;
            }

            @Override
            public JsonObject apply(JsonObject json) {
                json.addProperty(GraphSchemaVersion.VERSION_FIELD, 1);
                return json;
            }
        };
        var codec = new JsonGraphCodec(new SimpleNodeRegistry(), List.of(migration));
        var assets = new GraphAssets(codec);

        var legacyJson = """
                {"id":"legacy","nodes":[],"edges":[],"parameters":[],"outputs":[]}
                """;

        var graph = assets.load("legacy", legacyJson);
        assertEquals("legacy", graph.id());
    }
}
