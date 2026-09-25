package org.academy.api.client.render.graph.serialize;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GraphMigrationsTest {
    @Test
    void missingVersionTreatedAsZero() {
        var json = new JsonObject();
        json.addProperty("id", "g");
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
        var migrated = GraphMigrations.apply(json, List.of(migration));
        assertEquals(GraphSchemaVersion.CURRENT, migrated.get(GraphSchemaVersion.VERSION_FIELD).getAsInt());
    }

    @Test
    void migrationsAppliedInFromVersionOrder() {
        var json = new JsonObject();
        json.addProperty(GraphSchemaVersion.VERSION_FIELD, 0);
        var order = new StringBuilder();
        var m1 = new GraphMigration() {
            @Override
            public int fromVersion() {
                return 0;
            }

            @Override
            public JsonObject apply(JsonObject json) {
                order.append("a");
                json.addProperty(GraphSchemaVersion.VERSION_FIELD, 1);
                return json;
            }
        };
        var m2 = new GraphMigration() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public JsonObject apply(JsonObject json) {
                order.append("b");
                json.addProperty(GraphSchemaVersion.VERSION_FIELD, 2);
                return json;
            }
        };
        // 乱序传入，仍按 fromVersion 升序应用
        GraphMigrations.apply(json, List.of(m2, m1));
        assertEquals("ab", order.toString());
        assertEquals(2, json.get(GraphSchemaVersion.VERSION_FIELD).getAsInt());
    }

    @Test
    void staleMigrationsSkipped() {
        var json = new JsonObject();
        json.addProperty(GraphSchemaVersion.VERSION_FIELD, 3);
        var migration = new GraphMigration() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public JsonObject apply(JsonObject json) {
                throw new AssertionError("stale migration must not run");
            }
        };
        var migrated = GraphMigrations.apply(json, List.of(migration));
        assertEquals(3, migrated.get(GraphSchemaVersion.VERSION_FIELD).getAsInt());
    }

    @Test
    void schemaVersionConstants() {
        assertNotNull(GraphSchemaVersion.VERSION_FIELD);
        assertEquals(1, GraphSchemaVersion.CURRENT);
    }
}
