package org.academy.internal.common.ability.program;

import com.mojang.serialization.JsonOps;
import org.academy.api.common.ability.program.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Resolves a validated graph into stable input slots and control-flow targets.
 */
public final class ProgramCompiler {
    private ProgramCompiler() {
    }

    public static ProgramCompileResult compile(
            ProgramGraph graph,
            ProgramCompileContext context,
            ProgramNodeLookup lookup
    ) {
        var validation = ProgramGraphValidator.validate(graph, context, lookup);
        if (!validation.valid()) return new ProgramCompileResult(null, validation.diagnostics());
        if (graph.nodes().isEmpty()) {
            return new ProgramCompileResult(
                    null,
                    List.of(ProgramDiagnostic.graph(ProgramDiagnosticCode.EMPTY_PROGRAM))
            );
        }

        var diagnostics = new ArrayList<ProgramDiagnostic>();
        var nodes = new HashMap<Integer, CompiledProgram.CompiledNode>();
        var entryNodeId = -1;
        for (var node : graph.nodes()) {
            var type = lookup.find(node.type());
            if (type == null) {
                diagnostics.add(ProgramDiagnostic.node(ProgramDiagnosticCode.UNKNOWN_NODE_TYPE, node.id()));
                continue;
            }
            var compiled = compileNode(type, node);
            if (compiled == null) {
                diagnostics.add(ProgramDiagnostic.node(
                        ProgramDiagnosticCode.INVALID_CONFIGURATION,
                        node.id()
                ));
                continue;
            }
            nodes.put(node.id(), compiled);
            if (compiled.role() == ProgramNodeRole.ENTRY) {
                entryNodeId = node.id();
            }
        }
        if (!diagnostics.isEmpty()) return new ProgramCompileResult(null, diagnostics);

        var inputs = new HashMap<CompiledProgram.InputKey, List<CompiledProgram.OutputKey>>();
        var flowTargets = new HashMap<CompiledProgram.FlowKey, Integer>();
        var dataOutgoing = new HashMap<Integer, List<Integer>>();
        var dataIndegree = new HashMap<Integer, Integer>();
        nodes.keySet().forEach(id -> dataIndegree.put(id, 0));
        for (var edge : graph.edges()) {
            var from = nodes.get(edge.from().nodeId());
            var output = from.schema().output(edge.from().port()).orElseThrow();
            if (output.type().equals(ProgramValueTypes.FLOW)) {
                var key = new CompiledProgram.FlowKey(edge.from().nodeId(), edge.from().port());
                if (flowTargets.putIfAbsent(key, edge.to().nodeId()) != null) {
                    diagnostics.add(new ProgramDiagnostic(
                            ProgramDiagnosticCode.AMBIGUOUS_FLOW,
                            edge.from().nodeId(),
                            edge.from().port()
                    ));
                }
                continue;
            }
            var input = new CompiledProgram.InputKey(edge.to().nodeId(), edge.to().port());
            inputs.computeIfAbsent(input, _ -> new ArrayList<>()).add(
                    new CompiledProgram.OutputKey(edge.from().nodeId(), edge.from().port())
            );
            dataOutgoing.computeIfAbsent(edge.from().nodeId(), _ -> new ArrayList<>())
                    .add(edge.to().nodeId());
            dataIndegree.computeIfPresent(edge.to().nodeId(), (_, degree) -> degree + 1);
        }
        if (!diagnostics.isEmpty()) return new ProgramCompileResult(null, diagnostics);

        if (entryNodeId < 0) {
            var incoming = new HashSet<>(flowTargets.values());
            var roots = nodes.values().stream()
                    .filter(node -> node.role().requiresFlow())
                    .filter(node -> !incoming.contains(node.id()))
                    .sorted(Comparator.comparingInt(
                            CompiledProgram.CompiledNode::id))
                    .toList();
            if (roots.size() != 1) {
                diagnostics.add(ProgramDiagnostic.graph(ProgramDiagnosticCode.NO_ENTRY));
                return new ProgramCompileResult(null, diagnostics);
            }
            entryNodeId = roots.getFirst().id();
        }

        var pending = new PriorityQueue<Integer>();
        dataIndegree.forEach((id, degree) -> {
            if (degree == 0) pending.add(id);
        });
        var dataOrder = new ArrayList<Integer>(nodes.size());
        while (!pending.isEmpty()) {
            var current = pending.remove();
            dataOrder.add(current);
            for (var target : dataOutgoing.getOrDefault(current, List.of())) {
                var next = dataIndegree.computeIfPresent(target, (_, degree) -> degree - 1);
                if (next != null && next == 0) pending.add(target);
            }
        }
        return new ProgramCompileResult(
                new CompiledProgram(graph, entryNodeId, nodes, dataOrder, inputs, flowTargets),
                List.of()
        );
    }

    private static <C> CompiledProgram.CompiledNode compileNode(
            ProgramNodeType<C> type,
            ProgramGraph.Node node
    ) {
        try {
            var configuration = type.configurationCodec()
                    .parse(JsonOps.INSTANCE, node.configuration())
                    .result()
                    .orElse(null);
            if (configuration == null) return null;
            var schema = type.schema(configuration);
            return new CompiledProgram.CompiledNode(
                    node.id(),
                    node.type(),
                    type,
                    configuration,
                    type.role(),
                    schema
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
