package org.academy.api.client.render.vfxgraph.model;

import org.academy.api.client.render.graph.model.Edge;

/**
 * 数据边（M23）：算子输出端口 → 块/算子输入端口（数据流，驱动块属性）。
 * 引用统一走 {@link Edge.PortRef}（nodeId/portId），nodeId 在块与算子间全局唯一。
 *
 * @param from 源输出端口
 * @param to   目标输入端口
 */
public record VfxDataEdge(Edge.PortRef from, Edge.PortRef to) {
}
