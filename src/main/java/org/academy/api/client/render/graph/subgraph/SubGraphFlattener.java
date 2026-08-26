package org.academy.api.client.render.graph.subgraph;

import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.GraphParameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 子图内联展开器（M12-05）：把图中的 `subgraph` 节点替换为其引用的子图节点/边，ID 重映射。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>子图节点的输入端口 `in&lt;i&gt;` 对应子图的第 i 个参数；若父图向 `in&lt;i&gt;` 连边，
 *       则子图内引用该参数的 `input.param_*` 节点被删除，消费边直接改接父图源端口；未连边则参数提升为顶层参数。</li>
 *   <li>子图节点的输出端口 `out` 对应子图的输出节点（如 `output.color`）；父图从 `out` 引出的边
 *       改接子图输出节点的输入源。</li>
 *   <li>仅一层展开（嵌套子图本里程碑不支持，编译期保留会报「无生成器」）。</li>
 * </ul>
 */
public final class SubGraphFlattener {
    private SubGraphFlattener() {
    }

    public static Graph flatten(Graph graph, SubGraphRegistry registry) {
        boolean hasSub = graph.nodes().stream().anyMatch(n -> "subgraph".equals(n.type()));
        if (!hasSub || registry == null) return graph;

        var newNodes = new ArrayList<GraphNode>();
        var newEdges = new ArrayList<Edge>();
        var newParams = new ArrayList<GraphParameter>(graph.parameters());

        for (var node : graph.nodes()) {
            if (!"subgraph".equals(node.type())) {
                newNodes.add(node);
                continue;
            }
            var sub = registry.find(node.properties().getOrDefault("graph", ""));
            if (sub == null) {
                newNodes.add(node);
                continue;
            }
            inline(node, sub, graph.edges(), newNodes, newEdges, newParams);
        }
        return new Graph(graph.id(), newNodes, newEdges, newParams, graph.outputs());
    }

    private static void inline(
            GraphNode subNode,
            Graph sub,
            List<Edge> parentEdges,
            List<GraphNode> newNodes,
            List<Edge> newEdges,
            List<GraphParameter> newParams
    ) {
        Map<String, Edge> inEdges = new HashMap<>();
        List<Edge> outEdges = new ArrayList<>();
        for (var e : parentEdges) {
            if (e.to().nodeId().equals(subNode.id())) inEdges.put(e.to().portId(), e);
            if (e.from().nodeId().equals(subNode.id())) outEdges.add(e);
        }

        // 参数 -> 父图源（被覆盖的参数）
        Map<String, Edge.PortRef> paramOverride = new HashMap<>();
        for (int i = 0; i < sub.parameters().size(); i++) {
            var pe = inEdges.get("in" + i);
            if (pe != null) paramOverride.put(sub.parameters().get(i).id(), pe.from());
        }

        // 子图内部边 → 父图边的扩展列表
        var subEdges = new ArrayList<Edge>(sub.edges());

        Map<String, String> idMap = new HashMap<>();
        for (var sn : sub.nodes()) {
            if (isOutputNode(sn.type())) continue;
            idMap.put(sn.id(), subNode.id() + "_" + sn.id());
        }

        // 添加子图节点（跳过输出节点与被覆盖的 param 节点）
        for (var sn : sub.nodes()) {
            if (isOutputNode(sn.type())) continue;
            if (isParamNode(sn.type()) && paramOverride.containsKey(sn.properties().get("param"))) continue;
            newNodes.add(new GraphNode(idMap.get(sn.id()), sn.type(), sn.properties(), sn.ports(), sn.x(), sn.y()));
        }

        // 子图边：重映射；param 节点源被覆盖 → 接父图源；到输出节点 → 接父图出边
        for (var e : subEdges) {
            var fromNode = findNode(sub, e.from().nodeId());
            Edge.PortRef fromRef;
            if (fromNode != null && isParamNode(fromNode.type())) {
                var override = paramOverride.get(fromNode.properties().get("param"));
                if (override == null) {
                    fromRef = new Edge.PortRef(idMap.get(e.from().nodeId()), e.from().portId());
                } else {
                    fromRef = override;
                }
            } else {
                var fromId = idMap.get(e.from().nodeId());
                if (fromId == null) continue;
                fromRef = new Edge.PortRef(fromId, e.from().portId());
            }

            var toId = idMap.get(e.to().nodeId());
            if (toId == null) {
                // 到输出节点：父图从 subNode.out 引出的边改接 fromRef
                for (var oe : outEdges) {
                    newEdges.add(new Edge(fromRef, oe.to()));
                }
            } else {
                newEdges.add(new Edge(fromRef, new Edge.PortRef(toId, e.to().portId())));
            }
        }

        // 未覆盖参数提升为顶层参数
        for (var p : sub.parameters()) {
            if (!paramOverride.containsKey(p.id())) newParams.add(p);
        }
    }

    private static boolean isOutputNode(String typeId) {
        return typeId.startsWith("output.");
    }

    private static boolean isParamNode(String typeId) {
        return typeId.startsWith("input.param_");
    }

    private static GraphNode findNode(Graph graph, String id) {
        for (var n : graph.nodes()) {
            if (n.id().equals(id)) return n;
        }
        return null;
    }
}
