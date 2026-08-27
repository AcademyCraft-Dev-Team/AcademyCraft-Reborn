package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Reverse adapter used only by legacy Precision editor surfaces and data migration.
 */
public final class PrecisionProgramExporter {
    private PrecisionProgramExporter() {
    }

    public static ExportResult export(@Nullable AbilityProgram program) {
        if (program == null || program.graph().nodes().isEmpty()) {
            return new ExportResult(PrecisionGraph.EMPTY, PrecisionGraph.Diagnostic.OK);
        }
        if (program.schemaVersion() != AbilityProgram.CURRENT_SCHEMA_VERSION
                || !program.category().equals(PrecisionProgramNodeCatalog.MENTALOUT)) return malformed();
        var nodes = new ArrayList<PrecisionGraph.Node>();
        var byId = new HashMap<Integer, ExportNode>();
        var entryId = -1;
        for (var node : program.graph().nodes()) {
            if (node.schemaVersion() != 1) return malformed();
            if (node.type().equals(PrecisionProgramNodeIds.ON_CAST)) {
                if (entryId >= 0) return malformed();
                entryId = node.id();
                continue;
            }
            var kind = PrecisionProgramNodeIds.kind(node.type());
            var alias = kind == null ? PrecisionProgramAliases.canonical(node.type()) : null;
            if (kind == null && alias == null) return malformed();
            if (alias != null) kind = alias.legacyKind();
            final double parameter;
            if (alias != null) {
                parameter = kind.defaultParameter();
            } else {
                var configuration = node.configuration();
                if (!configuration.isJsonObject()
                        || !configuration.getAsJsonObject().has("parameter")) return malformed();
                try {
                    parameter = configuration.getAsJsonObject().get("parameter").getAsDouble();
                } catch (RuntimeException exception) {
                    return malformed();
                }
            }
            if (!Double.isFinite(parameter)) return malformed();
            var position = program.editorLayout().nodePositions().get(node.id());
            nodes.add(new PrecisionGraph.Node(
                    node.id(),
                    kind,
                    parameter,
                    position == null ? 0.0 : position.x(),
                    position == null ? 0.0 : position.y()
            ));
            byId.put(node.id(), new ExportNode(kind, alias));
        }
        if (!nodes.isEmpty() && entryId < 0) return malformed();

        var edges = new ArrayList<PrecisionGraph.Edge>();
        for (var edge : program.graph().edges()) {
            if (edge.from().nodeId() == entryId) {
                if (!edge.from().port().equals("flow") || !byId.containsKey(edge.to().nodeId())) {
                    return malformed();
                }
                continue;
            }
            if (edge.to().nodeId() == entryId) return malformed();
            var from = byId.get(edge.from().nodeId());
            var to = byId.get(edge.to().nodeId());
            if (from == null || to == null) return malformed();
            var fromName = from.alias() == null
                    ? edge.from().port()
                    : from.alias().legacyOutput(edge.from().port());
            var toName = to.alias() == null
                    ? edge.to().port()
                    : to.alias().legacyInput(edge.to().port());
            var fromPort = outputPort(from.kind(), fromName);
            var toPort = inputPort(to.kind(), toName);
            if (fromPort < 0 || toPort < 0) return malformed();
            edges.add(new PrecisionGraph.Edge(
                    edge.from().nodeId(),
                    fromPort,
                    edge.to().nodeId(),
                    toPort
            ));
        }
        var validation = new PrecisionGraph(nodes, edges).validate();
        return validation.valid()
                ? new ExportResult(validation.normalized(), PrecisionGraph.Diagnostic.OK)
                : new ExportResult(PrecisionGraph.EMPTY, validation.diagnostic());
    }

    private static int inputPort(PrecisionGraph.NodeKind kind, String name) {
        for (var index = 0; index < kind.inputDefinitions().size(); index++) {
            if (kind.inputDefinitions().get(index).key().equals(name)) return index;
        }
        return -1;
    }

    private static int outputPort(PrecisionGraph.NodeKind kind, String name) {
        for (var index = 0; index < kind.outputDefinitions().size(); index++) {
            if (kind.outputDefinitions().get(index).key().equals(name)) return index;
        }
        return -1;
    }

    private static ExportResult malformed() {
        return new ExportResult(PrecisionGraph.EMPTY, PrecisionGraph.Diagnostic.MALFORMED);
    }

    public record ExportResult(
            PrecisionGraph graph,
            PrecisionGraph.Diagnostic diagnostic
    ) {
        public boolean valid() {
            return diagnostic == PrecisionGraph.Diagnostic.OK;
        }
    }

    private record ExportNode(
            PrecisionGraph.NodeKind kind,
            PrecisionProgramAliases.@Nullable Alias alias
    ) {
    }
}
