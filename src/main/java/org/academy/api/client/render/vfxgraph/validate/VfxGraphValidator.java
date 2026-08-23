package org.academy.api.client.render.vfxgraph.validate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.academy.api.client.render.graph.model.Port;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.registry.NodeRegistry;
import org.academy.api.client.render.graph.type.TypeConverter;
import org.academy.api.client.render.graph.type.TypeConversions;
import org.academy.api.client.render.graph.validate.GraphIssue;
import org.academy.api.client.render.vfxgraph.model.VfxContextType;
import org.academy.api.client.render.vfxgraph.model.VfxNode;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;

/**
 * VFX 容器图校验器（M23）：context 引用、flow 连通/无环、数据边引用与类型兼容、输出存在。
 *
 * <p>校验规则：context id 唯一；flow 边引用存在且成 DAG；每个非 SPAWN context 有上游 flow；
 * 至少一个 SPAWN 与一个 OUTPUT context；数据边源/目标节点存在、方向与类型兼容；输出块存在。</p>
 */
public final class VfxGraphValidator {
    private final NodeRegistry registry;
    private final TypeConverter converter;

    public VfxGraphValidator(NodeRegistry registry) {
        this(registry, TypeConversions.INSTANCE);
    }

    public VfxGraphValidator(NodeRegistry registry, TypeConverter converter) {
        this.registry = registry;
        this.converter = converter;
    }

    public List<GraphIssue> validate(VfxSystem system) {
        var issues = new ArrayList<GraphIssue>();
        var contexts = new HashMap<String, org.academy.api.client.render.vfxgraph.model.VfxContext>();
        var nodes = new HashMap<String, VfxNode>();

        for (var ctx : system.contexts()) {
            if (contexts.putIfAbsent(ctx.id(), ctx) != null) {
                issues.add(GraphIssue.error("duplicate context id: " + ctx.id()));
                continue;
            }
            for (var block : ctx.blocks()) {
                checkNode(block, nodes, issues);
            }
        }
        for (var op : system.operators()) {
            checkNode(op, nodes, issues);
        }

        checkFlow(system, contexts, issues);
        checkBlockFlows(system, nodes, issues);
        checkDataEdges(system, nodes, issues);
        checkOutputs(system, nodes, issues);
        return issues;
    }

    private void checkNode(VfxNode node, Map<String, VfxNode> nodes, List<GraphIssue> issues) {
        if (nodes.putIfAbsent(node.id(), node) != null) {
            issues.add(GraphIssue.error("duplicate node id: " + node.id(), node.id()));
            return;
        }
        if (registry.find(node.type()) == null) {
            issues.add(GraphIssue.error("unknown node type: " + node.type(), node.id()));
        }
    }

    private void checkFlow(VfxSystem system, Map<String, org.academy.api.client.render.vfxgraph.model.VfxContext> contexts,
                           List<GraphIssue> issues) {
        if (system.contexts().isEmpty()) {
            issues.add(GraphIssue.error("vfx system has no contexts"));
            return;
        }
        if (system.contexts().stream().noneMatch(c -> c.type() == VfxContextType.SPAWN)) {
            issues.add(GraphIssue.error("vfx system has no SPAWN context"));
        }
        if (system.contexts().stream().noneMatch(c -> c.type() == VfxContextType.OUTPUT)) {
            issues.add(GraphIssue.error("vfx system has no OUTPUT context"));
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        for (var edge : system.flowEdges()) {
            if (!contexts.containsKey(edge.fromContextId())) {
                issues.add(GraphIssue.error("flow from missing context: " + edge.fromContextId()));
                continue;
            }
            if (!contexts.containsKey(edge.toContextId())) {
                issues.add(GraphIssue.error("flow to missing context: " + edge.toContextId()));
                continue;
            }
            adjacency.computeIfAbsent(edge.fromContextId(), _ -> new ArrayList<>()).add(edge.toContextId());
        }

        // flow 无环（DFS）
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (var ctxId : contexts.keySet()) {
            if (detectFlowCycle(ctxId, adjacency, visiting, visited, issues)) {
                break;
            }
        }

        // 非 SPAWN context 必须有上游 flow（批次来源）
        var inDegree = new HashSet<String>();
        for (var edge : system.flowEdges()) {
            inDegree.add(edge.toContextId());
        }
        for (var ctx : system.contexts()) {
            if (ctx.type() != VfxContextType.SPAWN && !inDegree.contains(ctx.id())) {
                issues.add(GraphIssue.error("context has no upstream flow: " + ctx.id(), ctx.id()));
            }
        }
    }

    private boolean detectFlowCycle(String ctxId, Map<String, List<String>> adjacency,
                                    Set<String> visiting, Set<String> visited, List<GraphIssue> issues) {
        if (visited.contains(ctxId)) return false;
        if (visiting.contains(ctxId)) {
            issues.add(GraphIssue.error("flow cycle detected involving context: " + ctxId, ctxId));
            return true;
        }
        visiting.add(ctxId);
        for (var next : adjacency.getOrDefault(ctxId, List.of())) {
            if (detectFlowCycle(next, adjacency, visiting, visited, issues)) {
                return true;
            }
        }
        visiting.remove(ctxId);
        visited.add(ctxId);
        return false;
    }

    /** 块级 flow：源必须是 SPAWN context 内的块，目标必须是 INITIALIZE context 内的块。 */
    private void checkBlockFlows(VfxSystem system, Map<String, VfxNode> nodes, List<GraphIssue> issues) {
        var blockContextType = new HashMap<String, VfxContextType>();
        for (var ctx : system.contexts()) {
            for (var block : ctx.blocks()) {
                blockContextType.put(block.id(), ctx.type());
            }
        }
        for (var edge : system.blockFlows()) {
            if (!nodes.containsKey(edge.fromBlockId())) {
                issues.add(GraphIssue.error("block flow from missing block: " + edge.fromBlockId()));
                continue;
            }
            if (!nodes.containsKey(edge.toBlockId())) {
                issues.add(GraphIssue.error("block flow to missing block: " + edge.toBlockId()));
                continue;
            }
            if (blockContextType.get(edge.fromBlockId()) != VfxContextType.SPAWN) {
                issues.add(GraphIssue.error("block flow source is not in a SPAWN context: " + edge.fromBlockId(), edge.fromBlockId()));
            }
            if (blockContextType.get(edge.toBlockId()) != VfxContextType.INITIALIZE) {
                issues.add(GraphIssue.error("block flow target is not in an INITIALIZE context: " + edge.toBlockId(), edge.toBlockId()));
            }
        }
    }

    private void checkDataEdges(VfxSystem system, Map<String, VfxNode> nodes, List<GraphIssue> issues) {        for (var edge : system.dataEdges()) {
            var fromNode = nodes.get(edge.from().nodeId());
            var toNode = nodes.get(edge.to().nodeId());
            if (fromNode == null) {
                issues.add(GraphIssue.error("data edge from missing node: " + edge.from().nodeId()));
                continue;
            }
            if (toNode == null) {
                issues.add(GraphIssue.error("data edge to missing node: " + edge.to().nodeId()));
                continue;
            }
            var fromPort = findPort(fromNode, edge.from().portId());
            var toPort = findPort(toNode, edge.to().portId());
            if (fromPort == null) {
                issues.add(GraphIssue.error("data edge from missing port: " + edge.from().portId(), fromNode.id()));
                continue;
            }
            if (toPort == null) {
                issues.add(GraphIssue.error("data edge to missing port: " + edge.to().portId(), toNode.id()));
                continue;
            }
            if (fromPort.direction() != PortDirection.OUTPUT) {
                issues.add(GraphIssue.error("data edge source is not an output port: " + fromPort.id(), fromNode.id()));
            }
            if (toPort.direction() != PortDirection.INPUT) {
                issues.add(GraphIssue.error("data edge target is not an input port: " + toPort.id(), toNode.id()));
            }
            if (!converter.canConvert(fromPort.type(), toPort.type())) {
                issues.add(GraphIssue.error(
                        "incompatible types: " + fromPort.type() + " -> " + toPort.type(), toNode.id()));
            }
        }
    }

    private void checkOutputs(VfxSystem system, Map<String, VfxNode> nodes, List<GraphIssue> issues) {
        if (system.outputs().isEmpty()) {
            issues.add(GraphIssue.error("vfx system has no output block"));
            return;
        }
        for (var outputId : system.outputs()) {
            if (!nodes.containsKey(outputId)) {
                issues.add(GraphIssue.error("output references missing node: " + outputId));
            }
        }
    }

    private static Port findPort(VfxNode node, String portId) {
        for (var port : node.ports()) {
            if (port.id().equals(portId)) return port;
        }
        return null;
    }
}
