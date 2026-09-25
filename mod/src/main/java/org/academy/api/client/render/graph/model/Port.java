package org.academy.api.client.render.graph.model;

import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;

/**
 * 节点端口（契约）。属于某个 {@link GraphNode}，有方向、类型与默认值。
 */
public record Port(String id, String name, PortDirection direction, ValueType type, Value defaultValue) {
}
