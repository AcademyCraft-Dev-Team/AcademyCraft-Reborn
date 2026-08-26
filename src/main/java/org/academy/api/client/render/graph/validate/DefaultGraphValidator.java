package org.academy.api.client.render.graph.validate;

import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.Port;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.type.TypeConversions;
import org.academy.api.client.render.graph.type.TypeConverter;

import java.util.*;

/**
 * 默认图校验器（契约实现）。检查节点类型存在、端口/端点引用、类型兼容、输出存在与环。
 */
public final class DefaultGraphValidator implements GraphValidator {
    private final NodeRegistry registry;
    private final TypeConverter converter;

    public DefaultGraphValidator(NodeRegistry registry) {
        this(registry, TypeConversions.INSTANCE);
    }

    public DefaultGraphValidator(NodeRegistry registry, TypeConverter converter) {
        this.registry = registry;
        this.converter = converter;
    }

    @Override
    public List<GraphIssue> validate(Graph graph) {
        var issues = new ArrayList<GraphIssue>();
        var nodesById = new HashMap<String, GraphNode>();

        for (var node : graph.nodes()) {
            if (nodesById.putIfAbsent(node.id(), node) != null) {
                issues.add(GraphIssue.error("duplicate node id: " + node.id(), node.id()));
                continue;
            }
            if (registry.find(node.type()) == null) {
                issues.add(GraphIssue.error("unknown node type: " + node.type(), node.id()));
            }
        }

        checkEdges(graph, nodesById, issues);
        checkOutputs(graph, nodesById, issues);
        checkCycles(graph, nodesById, issues);
        return issues;
    }

    private void checkEdges(Graph graph, Map<String, GraphNode> nodesById, List<GraphIssue> issues) {
        for (var edge : graph.edges()) {
            var fromNode = nodesById.get(edge.from().nodeId());
            var toNode = nodesById.get(edge.to().nodeId());
            if (fromNode == null) {
                issues.add(GraphIssue.error("edge from missing node: " + edge.from().nodeId()));
                continue;
            }
            if (toNode == null) {
                issues.add(GraphIssue.error("edge to missing node: " + edge.to().nodeId()));
                continue;
            }
            var fromPort = findPort(fromNode, edge.from().portId());
            var toPort = findPort(toNode, edge.to().portId());
            if (fromPort == null) {
                issues.add(GraphIssue.error(
                        "edge from missing port: " + edge.from().portId(), fromNode.id()));
                continue;
            }
            if (toPort == null) {
                issues.add(GraphIssue.error(
                        "edge to missing port: " + edge.to().portId(), toNode.id()));
                continue;
            }
            if (fromPort.direction() != PortDirection.OUTPUT) {
                issues.add(GraphIssue.error(
                        "edge source is not an output port: " + fromPort.id(), fromNode.id()));
            }
            if (toPort.direction() != PortDirection.INPUT) {
                issues.add(GraphIssue.error(
                        "edge target is not an input port: " + toPort.id(), toNode.id()));
            }
            if (!converter.canConvert(fromPort.type(), toPort.type())) {
                issues.add(GraphIssue.error(
                        "incompatible types: " + fromPort.type() + " -> " + toPort.type(), toNode.id()));
            }
        }
    }

    private void checkOutputs(Graph graph, Map<String, GraphNode> nodesById, List<GraphIssue> issues) {
        if (graph.outputs().isEmpty()) {
            issues.add(GraphIssue.error("graph has no output node"));
            return;
        }
        for (var outputId : graph.outputs()) {
            if (!nodesById.containsKey(outputId)) {
                issues.add(GraphIssue.error("output references missing node: " + outputId));
            }
        }
    }

    private void checkCycles(Graph graph, Map<String, GraphNode> nodesById, List<GraphIssue> issues) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (var edge : graph.edges()) {
            adjacency.computeIfAbsent(edge.from().nodeId(), _ -> new ArrayList<>()).add(edge.to().nodeId());
        }

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (var nodeId : nodesById.keySet()) {
            if (detectCycle(nodeId, adjacency, visiting, visited, issues)) {
                return;
            }
        }
    }

    private boolean detectCycle(String nodeId, Map<String, List<String>> adjacency,
                                Set<String> visiting, Set<String> visited, List<GraphIssue> issues) {
        if (visited.contains(nodeId)) return false;
        if (visiting.contains(nodeId)) {
            issues.add(GraphIssue.error("cycle detected involving node: " + nodeId, nodeId));
            return true;
        }
        visiting.add(nodeId);
        for (var next : adjacency.getOrDefault(nodeId, List.of())) {
            if (detectCycle(next, adjacency, visiting, visited, issues)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private static Port findPort(GraphNode node, String portId) {
        for (var port : node.ports()) {
            if (port.id().equals(portId)) return port;
        }
        return null;
    }
}
