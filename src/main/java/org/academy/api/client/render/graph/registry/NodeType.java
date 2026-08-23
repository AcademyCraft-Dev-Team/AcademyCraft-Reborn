package org.academy.api.client.render.graph.registry;

import java.util.List;

/**
 * 节点类型（契约）。节点的目录项（元数据），描述端口与属性。
 *
 * <p>节点语义（GLSL 代码生成 / VFX 模拟）由领域模块（shader/vfxgraph）按类型 id
 * 附加实现，不耦合在本元数据中。</p>
 */
public record NodeType(
        String id,
        String category,
        String displayName,
        List<PortSpec> ports,
        List<PropertySpec> properties
) {
    public NodeType {
        ports = List.copyOf(ports);
        properties = List.copyOf(properties);
    }
}
