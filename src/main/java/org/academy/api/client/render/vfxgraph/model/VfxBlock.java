package org.academy.api.client.render.vfxgraph.model;

import org.academy.api.client.render.graph.model.Port;

import java.util.List;
import java.util.Map;

/**
 * Context 内块（M23）。执行语义由块类型（{@code type}）按目录定义，与核心 {@code GraphNode} 同约定：
 * 端口由目录派生、属性以字符串化存储。块无画布坐标（在 context 内按列表顺序垂直排列，与 Unity 一致）。
 *
 * @param id         实例 id（全局唯一，供 flow/数据边引用）
 * @param type       块类型 id（如 {@code vfx.block.spawn_rate}）
 * @param properties 字符串化属性（键为 {@code PropertySpec.id()}）
 * @param ports      派生的端口（由 codec 从目录重建）
 */
public record VfxBlock(
        String id,
        String type,
        Map<String, String> properties,
        List<Port> ports
) implements VfxNode {
    public VfxBlock {
        properties = Map.copyOf(properties);
        ports = List.copyOf(ports);
    }
}
