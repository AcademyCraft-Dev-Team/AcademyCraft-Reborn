package org.academy.api.client.render.graph.compile;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.type.Value;

import java.util.List;
import java.util.Map;

/**
 * 编译结果（契约）。
 *
 * @param execOrder     拓扑排序后的执行顺序（已消除死节点与被折叠节点，输出节点保留）
 * @param parameterIds  黑板参数 id 列表（当前为图声明的全部参数）
 * @param foldedOutputs 被折叠节点的常量输出：nodeId → (outputPortId → 常量值)
 */
public record CompiledGraph(
        List<GraphNode> execOrder,
        List<String> parameterIds,
        Map<String, Map<String, Value>> foldedOutputs
) {
    public CompiledGraph {
        execOrder = List.copyOf(execOrder);
        parameterIds = List.copyOf(parameterIds);
        foldedOutputs = Map.copyOf(foldedOutputs);
    }
}
