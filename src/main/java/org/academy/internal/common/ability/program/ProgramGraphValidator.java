package org.academy.internal.common.ability.program;

import com.mojang.serialization.JsonOps;
import org.academy.api.common.ability.program.*;

import java.util.*;

/**
 * Structural, type and entitlement validation shared by the editor and server compiler.
 * Data dependencies remain acyclic while control-flow edges are intentionally allowed to loop.
 */
public final class ProgramGraphValidator {
    private static final int MAX_NODE_ID = 1_000_000;

    private ProgramGraphValidator() {
    }

    public static ProgramValidationResult validate(
            ProgramGraph graph,
            ProgramCompileContext context,
            ProgramNodeLookup lookup
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(lookup, "lookup");

        var diagnostics = new ArrayList<ProgramDiagnostic>();
        if (graph.nodes().size() > context.limits().maxNodes()) {
            diagnostics.add(ProgramDiagnostic.graph(ProgramDiagnosticCode.TOO_MANY_NODES));
        }
        if (graph.edges().size() > context.limits().maxEdges()) {
            diagnostics.add(ProgramDiagnostic.graph(ProgramDiagnosticCode.TOO_MANY_EDGES));
        }
        if (!diagnostics.isEmpty()) return new ProgramValidationResult(diagnostics);
        if (graph.nodes().isEmpty()) return new ProgramValidationResult(List.of());

        var resolved = resolveNodes(graph, context, lookup, diagnostics);
        var validEdges = validateEdges(graph, resolved, diagnostics);
        validateRequiredInputs(resolved, validEdges, diagnostics);
        validateEntryAndFlow(resolved, validEdges, diagnostics);
        validateDataAcyclic(resolved, validEdges, diagnostics);
        return new ProgramValidationResult(diagnostics);
    }

    private static Map<Integer, ResolvedNode> resolveNodes(
            ProgramGraph graph,
            ProgramCompileContext context,
            ProgramNodeLookup lookup,
            List<ProgramDiagnostic> diagnostics
    ) {
        var resolved = new HashMap<Integer, ResolvedNode>();
        var seenIds = new HashSet<Integer>();
        for (var node : graph.nodes()) {
            if (node == null || node.id() < 0 || node.id() > MAX_NODE_ID) {
                diagnostics.add(ProgramDiagnostic.node(
                        ProgramDiagnosticCode.INVALID_NODE,
                        node == null ? -1 : node.id()
                ));
                continue;
            }
            if (!seenIds.add(node.id())) {
                diagnostics.add(ProgramDiagnostic.node(ProgramDiagnosticCode.DUPLICATE_NODE, node.id()));
                continue;
            }
            var type = lookup.find(node.type());
            if (type == null) {
                diagnostics.add(ProgramDiagnostic.node(ProgramDiagnosticCode.UNKNOWN_NODE_TYPE, node.id()));
                continue;
            }
            if (node.schemaVersion() != type.schemaVersion()) {
                diagnostics.add(ProgramDiagnostic.node(
                        ProgramDiagnosticCode.UNSUPPORTED_NODE_SCHEMA,
                        node.id()
                ));
                continue;
            }
            var schema = decodeSchema(type, node);
            if (schema == null) {
                diagnostics.add(ProgramDiagnostic.node(
                        ProgramDiagnosticCode.INVALID_CONFIGURATION,
                        node.id()
                ));
                continue;
            }
            var scope = type.scope();
            if (!scope.allowsCategory(context.category())) {
                diagnostics.add(ProgramDiagnostic.node(ProgramDiagnosticCode.CATEGORY_MISMATCH, node.id()));
            }
            if (!context.capabilities().containsAll(scope.requiredCapabilities())) {
                diagnostics.add(ProgramDiagnostic.node(ProgramDiagnosticCode.CAPABILITY_MISSING, node.id()));
            }
            resolved.put(node.id(), new ResolvedNode(node, type.role(), schema));
        }
        return resolved;
    }

    private static <C> ProgramNodeSchema decodeSchema(
            ProgramNodeType<C> type,
            ProgramGraph.Node node
    ) {
        try {
            var configuration = type.configurationCodec()
                    .parse(JsonOps.INSTANCE, node.configuration())
                    .result()
                    .orElse(null);
            return configuration == null ? null : type.schema(configuration);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static List<ResolvedEdge> validateEdges(
            ProgramGraph graph,
            Map<Integer, ResolvedNode> nodes,
            List<ProgramDiagnostic> diagnostics
    ) {
        var result = new ArrayList<ResolvedEdge>();
        var unique = new HashSet<ProgramGraph.Edge>();
        var inputConnections = new HashMap<ProgramGraph.Endpoint, Integer>();
        var outputConnections = new HashMap<ProgramGraph.Endpoint, Integer>();
        for (var edge : graph.edges()) {
            if (edge == null) {
                diagnostics.add(ProgramDiagnostic.graph(ProgramDiagnosticCode.INVALID_EDGE));
                continue;
            }
            if (!unique.add(edge)) {
                diagnostics.add(new ProgramDiagnostic(
                        ProgramDiagnosticCode.DUPLICATE_EDGE,
                        edge.to().nodeId(),
                        edge.to().port()
                ));
                continue;
            }
            var fromNode = nodes.get(edge.from().nodeId());
            var toNode = nodes.get(edge.to().nodeId());
            if (fromNode == null || toNode == null) {
                diagnostics.add(new ProgramDiagnostic(
                        ProgramDiagnosticCode.INVALID_EDGE,
                        edge.to().nodeId(),
                        edge.to().port()
                ));
                continue;
            }
            var output = fromNode.schema.output(edge.from().port()).orElse(null);
            var input = toNode.schema.input(edge.to().port()).orElse(null);
            if (output == null) {
                diagnostics.add(new ProgramDiagnostic(
                        ProgramDiagnosticCode.UNKNOWN_PORT,
                        edge.from().nodeId(),
                        edge.from().port()
                ));
                continue;
            }
            if (input == null) {
                diagnostics.add(new ProgramDiagnostic(
                        ProgramDiagnosticCode.UNKNOWN_PORT,
                        edge.to().nodeId(),
                        edge.to().port()
                ));
                continue;
            }
            if (!ProgramValueTypes.canConnect(output.type(), input.type())) {
                diagnostics.add(new ProgramDiagnostic(
                        ProgramDiagnosticCode.TYPE_MISMATCH,
                        edge.to().nodeId(),
                        edge.to().port()
                ));
                continue;
            }
            var inputCount = inputConnections.merge(edge.to(), 1, Integer::sum);
            var outputCount = outputConnections.merge(edge.from(), 1, Integer::sum);
            if (inputCount > input.maxConnections() || outputCount > output.maxConnections()) {
                diagnostics.add(new ProgramDiagnostic(
                        ProgramDiagnosticCode.TOO_MANY_CONNECTIONS,
                        inputCount > input.maxConnections() ? edge.to().nodeId() : edge.from().nodeId(),
                        inputCount > input.maxConnections() ? edge.to().port() : edge.from().port()
                ));
                continue;
            }
            result.add(new ResolvedEdge(edge, output.type()));
        }
        return result;
    }

    private static void validateRequiredInputs(
            Map<Integer, ResolvedNode> nodes,
            List<ResolvedEdge> edges,
            List<ProgramDiagnostic> diagnostics
    ) {
        var connected = new HashSet<ProgramGraph.Endpoint>();
        edges.forEach(edge -> connected.add(edge.edge.to()));
        nodes.values().stream()
                .sorted(Comparator.comparingInt(node -> node.node.id()))
                .forEach(node -> {
                    for (var input : node.schema.inputs()) {
                        if (input.required()
                                && !input.type().equals(ProgramValueTypes.FLOW)
                                && !connected.contains(new ProgramGraph.Endpoint(node.node.id(), input.name()))) {
                            diagnostics.add(new ProgramDiagnostic(
                                    ProgramDiagnosticCode.MISSING_INPUT,
                                    node.node.id(),
                                    input.name()
                            ));
                        }
                    }
                });
    }

    private static void validateEntryAndFlow(
            Map<Integer, ResolvedNode> nodes,
            List<ResolvedEdge> edges,
            List<ProgramDiagnostic> diagnostics
    ) {
        var flowEdges = edges.stream().filter(ResolvedEdge::flow).toList();
        var entries = nodes.values().stream()
                .filter(node -> node.role == ProgramNodeRole.ENTRY)
                .sorted(Comparator.comparingInt(node -> node.node.id()))
                .toList();
        ResolvedNode entry;
        var explicitEntry = !entries.isEmpty();
        if (entries.isEmpty()) {
            var incoming = new HashSet<Integer>();
            flowEdges.forEach(edge -> incoming.add(edge.edge.to().nodeId()));
            var roots = nodes.values().stream()
                    .filter(node -> node.role.requiresFlow())
                    .filter(node -> !incoming.contains(node.node.id()))
                    .sorted(Comparator.comparingInt(node -> node.node.id()))
                    .toList();
            if (roots.size() != 1) {
                diagnostics.add(ProgramDiagnostic.graph(ProgramDiagnosticCode.NO_ENTRY));
                return;
            }
            entry = roots.getFirst();
        } else if (entries.size() > 1) {
            diagnostics.add(ProgramDiagnostic.node(
                    ProgramDiagnosticCode.MULTIPLE_ENTRIES,
                    entries.get(1).node.id()
            ));
            return;
        } else {
            entry = entries.getFirst();
        }

        if (explicitEntry) {
            var hasEntryOutput = entry.schema.outputs().stream()
                    .map(ProgramPortDefinition::type)
                    .anyMatch(ProgramValueTypes.FLOW::equals);
            var entryHasIncoming = flowEdges.stream()
                    .anyMatch(edge -> edge.edge.to().nodeId() == entry.node.id());
            if (!hasEntryOutput || entryHasIncoming) {
                diagnostics.add(ProgramDiagnostic.node(
                        ProgramDiagnosticCode.INVALID_ENTRY,
                        entry.node.id()
                ));
            }
        }

        var outgoing = new HashMap<Integer, List<Integer>>();
        for (var edge : flowEdges) {
            outgoing.computeIfAbsent(edge.edge.from().nodeId(), _ -> new ArrayList<>())
                    .add(edge.edge.to().nodeId());
        }
        var reachable = new HashSet<Integer>();
        var pending = new ArrayDeque<Integer>();
        pending.add(entry.node.id());
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (!reachable.add(current)) continue;
            outgoing.getOrDefault(current, List.of()).stream().sorted().forEach(pending::addLast);
        }
        nodes.values().stream()
                .filter(node -> node.role.requiresFlow() && !reachable.contains(node.node.id()))
                .sorted(Comparator.comparingInt(node -> node.node.id()))
                .forEach(node -> diagnostics.add(ProgramDiagnostic.node(
                        ProgramDiagnosticCode.UNREACHABLE_FLOW_NODE,
                        node.node.id()
                )));
    }

    private static void validateDataAcyclic(
            Map<Integer, ResolvedNode> nodes,
            List<ResolvedEdge> edges,
            List<ProgramDiagnostic> diagnostics
    ) {
        var outgoing = new HashMap<Integer, List<Integer>>();
        var indegree = new HashMap<Integer, Integer>();
        nodes.keySet().forEach(id -> indegree.put(id, 0));
        for (var edge : edges) {
            if (edge.flow()) continue;
            outgoing.computeIfAbsent(edge.edge.from().nodeId(), _ -> new ArrayList<>())
                    .add(edge.edge.to().nodeId());
            indegree.computeIfPresent(edge.edge.to().nodeId(), (_, value) -> value + 1);
        }
        var pending = new PriorityQueue<Integer>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) pending.add(id);
        });
        var visited = new HashSet<Integer>();
        while (!pending.isEmpty()) {
            var current = pending.remove();
            visited.add(current);
            for (var target : outgoing.getOrDefault(current, List.of())) {
                var next = indegree.computeIfPresent(target, (_, degree) -> degree - 1);
                if (next != null && next == 0) pending.add(target);
            }
        }
        if (visited.size() == nodes.size()) return;
        var cycleNode = nodes.keySet().stream()
                .filter(id -> !visited.contains(id))
                .mapToInt(Integer::intValue)
                .min()
                .orElse(-1);
        diagnostics.add(ProgramDiagnostic.node(ProgramDiagnosticCode.DATA_CYCLE, cycleNode));
    }

    private record ResolvedNode(
            ProgramGraph.Node node,
            ProgramNodeRole role,
            ProgramNodeSchema schema
    ) {
    }

    private record ResolvedEdge(ProgramGraph.Edge edge, ProgramValueType type) {
        private boolean flow() {
            return type.equals(ProgramValueTypes.FLOW);
        }
    }
}
