package org.academy.api.client.render.graph.subgraph;

import org.academy.api.client.render.graph.model.Graph;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 子图注册表：id → 子图资产（Graph）。
 */
public final class SubGraphRegistry {
    private final Map<String, Graph> graphs = new LinkedHashMap<>();

    public void register(String id, Graph graph) {
        graphs.put(id, graph);
    }

    public @Nullable Graph find(String id) {
        return graphs.get(id);
    }

    public void clear() {
        graphs.clear();
    }
}
