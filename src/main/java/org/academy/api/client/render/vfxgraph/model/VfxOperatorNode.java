package org.academy.api.client.render.vfxgraph.model;

import java.util.List;
import java.util.Map;
import org.academy.api.client.render.graph.model.Port;

/**
 * 自由算子节点（M23）：不在 context 内，经数据边（{@link VfxDataEdge}）驱动块的属性输入端口。
 * 语义（constant/math/curve/gradient/param/attr-read）由算子类型按目录定义。
 *
 * @param id        实例 id（全局唯一，供数据边引用）
 * @param type      算子类型 id（如 {@code vfx.op.attr_position}）
 * @param properties 字符串化属性
 * @param ports     派生的端口
 * @param x         画布坐标（自由放置）
 * @param y         画布坐标
 */
public record VfxOperatorNode(
        String id,
        String type,
        Map<String, String> properties,
        List<Port> ports,
        float x,
        float y
) implements VfxNode {
    public VfxOperatorNode {
        properties = Map.copyOf(properties);
        ports = List.copyOf(ports);
    }
}
