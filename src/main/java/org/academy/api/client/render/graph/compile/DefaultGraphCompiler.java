package org.academy.api.client.render.graph.compile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.GraphParameter;
import org.academy.api.client.render.graph.model.Port;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.validate.DefaultGraphValidator;
import org.academy.api.client.render.graph.validate.GraphIssue;
import org.academy.api.client.render.graph.validate.GraphValidator;
import org.jspecify.annotations.Nullable;

/**
 * 默认图编译器（契约实现）。校验 → 死代码消除 → 拓扑排序 → 常量折叠（可选）。
 */
public final class DefaultGraphCompiler implements GraphCompiler {
    private final GraphValidator validator;
    private final @Nullable NodeEvaluator evaluator;

    public DefaultGraphCompiler(NodeRegistry registry) {
        this(registry, null);
    }

    public DefaultGraphCompiler(NodeRegistry registry, @Nullable NodeEvaluator evaluator) {
        this(new DefaultGraphValidator(registry), evaluator);
    }

    public DefaultGraphCompiler(GraphValidator validator, @Nullable NodeEvaluator evaluator) {
        this.validator = validator;
        this.evaluator = evaluator;
    }

    @Override
    public CompiledGraph compile(Graph graph) {
        var errors = validator.validate(graph).stream()
                .filter(i -> i.severity() == GraphIssue.Severity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            throw new GraphCompileException(errors);
        }

        var reachable = reachableNodes(graph);
        var order = topoSort(graph, reachable);

        Map<String, Map<String, Value>> folded = Map.of();
        if (evaluator != null) {
            var result = fold(graph, order);
            order = result.order();
            folded = result.folded();
        }

        var parameterIds = graph.parameters().stream().map(GraphParameter::id).toList();
        return new CompiledGraph(order, parameterIds, folded);
    }

    /** 从输出节点反向可达的节点集合（死代码消除）。 */
    private static Set<String> reachableNodes(Graph graph) {
        Map<String, List<String>> reverse = new HashMap<>();
        for (var edge : graph.edges()) {
            reverse.computeIfAbsent(edge.to().nodeId(), _ -> new ArrayList<>()).add(edge.from().nodeId());
        }

        Set<String> reachable = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(graph.outputs());
        while (!queue.isEmpty()) {
            var id = queue.poll();
            if (!reachable.add(id)) continue;
            for (var pred : reverse.getOrDefault(id, List.of())) {
                if (!reachable.contains(pred)) queue.add(pred);
            }
        }
        return reachable;
    }

    private static List<GraphNode> topoSort(Graph graph, Set<String> reachable) {
        Map<String, GraphNode> byId = new LinkedHashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (var node : graph.nodes()) {
            if (!reachable.contains(node.id())) continue;
            byId.put(node.id(), node);
            adj.put(node.id(), new ArrayList<>());
            inDegree.put(node.id(), 0);
        }
        for (var edge : graph.edges()) {
            if (!reachable.contains(edge.from().nodeId()) || !reachable.contains(edge.to().nodeId())) continue;
            adj.get(edge.from().nodeId()).add(edge.to().nodeId());
            inDegree.merge(edge.to().nodeId(), 1, Integer::sum);
        }

        Deque<String> queue = new ArrayDeque<>();
        for (var id : byId.keySet()) {
            if (inDegree.get(id) == 0) queue.add(id);
        }

        List<GraphNode> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            var id = queue.poll();
            order.add(byId.get(id));
            for (var next : adj.get(id)) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) queue.add(next);
            }
        }

        if (order.size() != byId.size()) {
            throw new GraphCompileException(List.of(GraphIssue.error("cycle detected during topological sort")));
        }
        return order;
    }

    private FoldResult fold(Graph graph, List<GraphNode> order) {
        var evaluator = this.evaluator;
        assert evaluator != null;

        Set<String> outputIds = new HashSet<>(graph.outputs());
        Map<String, Edge> inputEdges = new HashMap<>();
        for (var edge : graph.edges()) {
            inputEdges.put(edge.to().nodeId() + ':' + edge.to().portId(), edge);
        }

        Map<String, Map<String, Value>> nodeConstants = new HashMap<>();
        Map<String, Map<String, Value>> folded = new LinkedHashMap<>();
        List<GraphNode> kept = new ArrayList<>();

        for (var node : order) {
            if (outputIds.contains(node.id())) {
                kept.add(node);
                continue;
            }

            Map<String, Value> inputs = new HashMap<>();
            boolean allConst = true;
            for (var port : node.ports()) {
                if (port.direction() != PortDirection.INPUT) continue;
                var edge = inputEdges.get(node.id() + ':' + port.id());
                if (edge == null) {
                    inputs.put(port.id(), port.defaultValue());
                    continue;
                }
                var src = nodeConstants.get(edge.from().nodeId());
                if (src == null || !src.containsKey(edge.from().portId())) {
                    allConst = false;
                    break;
                }
                inputs.put(port.id(), src.get(edge.from().portId()));
            }

            if (allConst) {
                var out = evaluator.evaluate(node, inputs);
                if (out.isPresent() && !out.get().isEmpty()) {
                    var values = out.get();
                    nodeConstants.put(node.id(), values);
                    folded.put(node.id(), values);
                    continue;
                }
            }
            kept.add(node);
        }

        return new FoldResult(kept, folded);
    }

    private record FoldResult(List<GraphNode> order, Map<String, Map<String, Value>> folded) {
    }
}
