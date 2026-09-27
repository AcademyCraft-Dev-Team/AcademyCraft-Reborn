package org.academy.api.client.render.graph.serialize;

import com.google.gson.JsonObject;
import org.academy.api.client.render.graph.model.Graph;

/**
 * 图编解码器（契约）。基于 Gson，JSON 顶层含 {@link GraphSchemaVersion#VERSION_FIELD}。
 */
public interface GraphCodec {
    JsonObject encode(Graph graph);

    Graph decode(JsonObject json);
}
