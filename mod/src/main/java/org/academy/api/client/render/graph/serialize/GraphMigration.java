package org.academy.api.client.render.graph.serialize;

import com.google.gson.JsonObject;

/**
 * 图资产迁移（契约）。把某个历史版本的 JSON 迁移到下一版本；迁移链按序应用。
 */
public interface GraphMigration {
    int fromVersion();

    JsonObject apply(JsonObject json);
}
