package org.academy.api.client.render.graph.registry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 内存节点目录（契约实现）。按 id 去重，重复注册抛异常。
 */
public final class SimpleNodeRegistry implements NodeRegistry {
    private final Map<String, NodeType> types = new LinkedHashMap<>();

    @Override
    public void register(NodeType type) {
        var existing = types.putIfAbsent(type.id(), type);
        if (existing != null && existing != type) {
            throw new IllegalStateException("duplicate node type id: " + type.id());
        }
    }

    @Override
    @Nullable
    public NodeType find(String id) {
        return types.get(id);
    }

    @Override
    public Collection<NodeType> all() {
        return types.values();
    }
}
