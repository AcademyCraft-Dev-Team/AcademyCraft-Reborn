package org.academy.api.client.render.graph.model;

import java.util.List;

/**
 * 图（契约）。可序列化的资产单元：节点 + 边 + 黑板参数 + 输出。
 */
public record Graph(
        String id,
        List<GraphNode> nodes,
        List<Edge> edges,
        List<GraphParameter> parameters,
        List<String> outputs
) {
    public Graph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        parameters = List.copyOf(parameters);
        outputs = List.copyOf(outputs);
    }
}
