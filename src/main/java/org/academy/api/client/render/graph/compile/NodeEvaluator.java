package org.academy.api.client.render.graph.compile;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.type.Value;

import java.util.Map;
import java.util.Optional;

/**
 * 节点求值器（契约）。领域模块（shader/vfx）提供，用于常量折叠。
 *
 * <p>给定节点实例与各输入端口的常量值，若该节点是纯函数（输出可确定为常量），
 * 返回输出端口 → 常量值的映射；否则返回空。</p>
 */
public interface NodeEvaluator {
    Optional<Map<String, Value>> evaluate(GraphNode node, Map<String, Value> inputs);
}
