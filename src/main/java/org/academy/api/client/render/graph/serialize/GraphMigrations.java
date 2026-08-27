package org.academy.api.client.render.graph.serialize;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 迁移链工具（契约实现）。把任意历史版本 JSON 按序迁移到当前版本。
 */
public final class GraphMigrations {
    private GraphMigrations() {
    }

    /**
     * 按 {@code fromVersion} 升序应用迁移，直到 {@link GraphSchemaVersion#CURRENT}。
     * 未携带版本字段的输入视为版本 0。返回迁移后的 JSON（输入不改动）。
     */
    public static JsonObject apply(JsonObject json, List<GraphMigration> migrations) {
        var current = json;
        var version = json.has(GraphSchemaVersion.VERSION_FIELD)
                ? json.get(GraphSchemaVersion.VERSION_FIELD).getAsInt()
                : 0;

        var ordered = new ArrayList<>(migrations);
        ordered.sort(Comparator.comparingInt(GraphMigration::fromVersion));

        for (var migration : ordered) {
            if (migration.fromVersion() < version) continue;
            current = migration.apply(current);
            version = migration.fromVersion() + 1;
        }
        return current;
    }
}
