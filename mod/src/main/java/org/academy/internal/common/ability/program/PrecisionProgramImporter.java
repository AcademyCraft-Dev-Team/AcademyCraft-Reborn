package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic, one-way import from the original Precision Operation graph format.
 */
public final class PrecisionProgramImporter {
    private PrecisionProgramImporter() {
    }

    public static ImportResult importGraph(PrecisionGraph source) {
        if (source == null || source.nodes().isEmpty()) {
            return new ImportResult(
                    ProgramGraph.EMPTY,
                    ProgramEditorLayout.EMPTY,
                    PrecisionGraph.Diagnostic.OK
            );
        }
        var validation = source.validate();
        if (!validation.valid()) {
            return new ImportResult(
                    ProgramGraph.EMPTY,
                    ProgramEditorLayout.EMPTY,
                    validation.diagnostic()
            );
        }
        return importNormalizedGraph(validation.normalized());
    }

    /**
     * Converts an in-progress legacy canvas without requiring all inputs or flow links yet.
     */
    public static ImportResult importEditableGraph(PrecisionGraph source) {
        if (source == null || source.nodes().isEmpty()) {
            return new ImportResult(
                    ProgramGraph.EMPTY,
                    ProgramEditorLayout.EMPTY,
                    PrecisionGraph.Diagnostic.OK
            );
        }
        return importNormalizedGraph(source);
    }

    private static ImportResult importNormalizedGraph(PrecisionGraph graph) {
        var nodes = new ArrayList<ProgramGraph.Node>();
        var edges = new ArrayList<ProgramGraph.Edge>();
        var positions = new HashMap<Integer, ProgramEditorLayout.NodePosition>();
        var byId = new HashMap<Integer, PrecisionGraph.Node>();
        for (var node : graph.nodes()) {
            byId.put(node.id(), node);
            var configuration = new JsonObject();
            var alias = PrecisionProgramAliases.legacy(node.kind());
            if (alias == null) configuration.addProperty("parameter", node.parameter());
            nodes.add(new ProgramGraph.Node(
                    node.id(),
                    alias == null
                            ? PrecisionProgramNodeIds.id(node.kind())
                            : alias.canonicalType(),
                    1,
                    configuration
            ));
            positions.put(node.id(), new ProgramEditorLayout.NodePosition(node.x(), node.y()));
        }
        for (var edge : graph.edges()) {
            var from = byId.get(edge.fromNode());
            var to = byId.get(edge.toNode());
            var fromPort = from.kind().outputDefinitions().get(edge.fromPort()).key();
            var toPort = to.kind().inputDefinitions().get(edge.toPort()).key();
            var fromAlias = PrecisionProgramAliases.legacy(from.kind());
            var toAlias = PrecisionProgramAliases.legacy(to.kind());
            edges.add(new ProgramGraph.Edge(
                    new ProgramGraph.Endpoint(
                            from.id(),
                            fromAlias == null ? fromPort : fromAlias.canonicalOutput(fromPort)
                    ),
                    new ProgramGraph.Endpoint(
                            to.id(),
                            toAlias == null ? toPort : toAlias.canonicalInput(toPort)
                    )
            ));
        }

        var entryId = firstFreeNodeId(graph.nodes());
        var entryConfiguration = new JsonObject();
        nodes.add(new ProgramGraph.Node(
                entryId,
                PrecisionProgramNodeIds.ON_CAST,
                1,
                entryConfiguration
        ));
        var flowTargets = graph.edges().stream()
                .filter(edge -> {
                    var from = byId.get(edge.fromNode());
                    return from != null && edge.fromPort() < from.kind().outputDefinitions().size()
                            && from.kind().outputDefinitions().get(edge.fromPort()).type()
                            == PrecisionGraph.PortType.FLOW;
                })
                .map(PrecisionGraph.Edge::toNode)
                .collect(Collectors.toSet());
        var firstAction = graph.nodes().stream()
                .filter(node -> node.kind().isAction() && !flowTargets.contains(node.id()))
                .min(Comparator.comparingInt(PrecisionGraph.Node::id))
                .orElse(null);
        if (firstAction != null) {
            edges.add(new ProgramGraph.Edge(
                    new ProgramGraph.Endpoint(entryId, "flow"),
                    new ProgramGraph.Endpoint(firstAction.id(), firstAction.kind().inputDefinitions()
                            .get(firstAction.kind().flowInputPort()).key())
            ));
        }
        positions.put(entryId, entryPosition(graph.nodes(), firstAction));
        return new ImportResult(
                new ProgramGraph(nodes, edges),
                new ProgramEditorLayout(positions),
                PrecisionGraph.Diagnostic.OK
        );
    }

    private static int firstFreeNodeId(List<PrecisionGraph.Node> nodes) {
        var used = new HashSet<Integer>();
        nodes.forEach(node -> used.add(node.id()));
        for (var id = 0; id <= 1_000_000; id++) {
            if (!used.contains(id)) return id;
        }
        throw new IllegalArgumentException("Precision graph has no free node id");
    }

    private static ProgramEditorLayout.NodePosition entryPosition(
            List<PrecisionGraph.Node> nodes,
            PrecisionGraph.@Nullable Node firstAction
    ) {
        var minX = nodes.stream().mapToDouble(PrecisionGraph.Node::x).min().orElse(0.0);
        var y = firstAction == null
                ? nodes.stream().mapToDouble(PrecisionGraph.Node::y).min().orElse(0.0)
                : firstAction.y();
        return new ProgramEditorLayout.NodePosition(minX - 96.0, y);
    }

    public record ImportResult(
            ProgramGraph graph,
            ProgramEditorLayout editorLayout,
            PrecisionGraph.Diagnostic diagnostic
    ) {
        public boolean valid() {
            return diagnostic == PrecisionGraph.Diagnostic.OK;
        }
    }
}
