package org.academy.api.client.render.graph.registry;

import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;

/**
 * 端口规格（契约）。节点目录中声明的端口定义。
 */
public record PortSpec(String id, String name, PortDirection direction, ValueType type, Value defaultValue) {
}
