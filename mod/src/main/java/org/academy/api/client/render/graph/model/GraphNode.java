package org.academy.api.client.render.graph.model;

import java.util.List;
import java.util.Map;

/**
 * 节点实例（契约）。图中某个 {@code NodeType} 的具体实例化，带 id、属性值与位置。
 *
 * <p>{@code properties()} 键为 {@code PropertySpec.id()}，值为字符串化的属性值
 * （具体编码由序列化层约定）。</p>
 */
public record GraphNode(
        String id,
        String type,
        Map<String, String> properties,
        List<Port> ports,
        float x,
        float y
) {
    public GraphNode {
        properties = Map.copyOf(properties);
        ports = List.copyOf(ports);
    }
}
