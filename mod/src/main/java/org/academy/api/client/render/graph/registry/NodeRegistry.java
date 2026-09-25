package org.academy.api.client.render.graph.registry;

import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * 节点目录（契约）。注册/查询 {@link NodeType}，供编辑器与编译期使用。
 */
public interface NodeRegistry {
    void register(NodeType type);

    @Nullable
    NodeType find(String id);

    Collection<NodeType> all();
}
