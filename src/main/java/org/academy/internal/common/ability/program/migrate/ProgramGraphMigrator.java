package org.academy.internal.common.ability.program.migrate;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.program.registry.ProgramNodeLookup;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Explicit program-graph migrator. It rewrites legacy action nodes into the unified structure
 * family and upgrades node configurations along registered schema versions without touching the
 * program schema version or discarding unmigratable data. Every failure is reported as a
 * per-node diagnostic while the rest of the graph stays intact.
 */
public final class ProgramGraphMigrator {
    public static final ProgramGraphMigrator EMPTY = builder().build();
    private static final int MAX_REWRITE_PASSES = 16;
    private static final double REWRITE_NODE_SPACING = 90.0;

    private final List<NodeRewrite> rewrites;
    private final Map<Identifier, NodeAudit> audits;
    private final Map<Identifier, List<SchemaUpgrade>> schemaUpgrades;

    private ProgramGraphMigrator(
            List<NodeRewrite> rewrites,
            Map<Identifier, NodeAudit> audits,
            Map<Identifier, List<SchemaUpgrade>> schemaUpgrades
    ) {
        this.rewrites = List.copyOf(rewrites);
        this.audits = Map.copyOf(audits);
        this.schemaUpgrades = Map.copyOf(schemaUpgrades);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Replaces one legacy node with one or more unified nodes. Ids inside a rewrite are
     * placeholder slot indices; the migrator allocates the real node ids.
     */
    public interface NodeRewrite {
        Identifier legacyId();

        /**
         * @return the replacement, or {@code null} when the node cannot be migrated; the
         * migrator then records {@link Diagnostic#REWRITE_FAILED} and keeps the node untouched
         */
        @Nullable Rewrite rewrite(ProgramGraph.Node node, ProgramNodeLookup lookup);
    }

    @FunctionalInterface
    public interface RewriteFunction {
        @Nullable Rewrite rewrite(ProgramGraph.Node node, ProgramNodeLookup lookup);
    }

    public static NodeRewrite rewrite(Identifier legacyId, RewriteFunction function) {
        Objects.requireNonNull(legacyId, "legacyId");
        Objects.requireNonNull(function, "function");
        return new NodeRewrite() {
            @Override
            public Identifier legacyId() {
                return legacyId;
            }

            @Override
            public @Nullable Rewrite rewrite(ProgramGraph.Node node, ProgramNodeLookup lookup) {
                return function.rewrite(node, lookup);
            }
        };
    }

    public record Rewrite(
            List<ProgramGraph.Node> nodes,
            List<ProgramGraph.Edge> internalEdges,
            Map<String, ProgramGraph.Endpoint> inputTargets,
            Map<String, ProgramGraph.Endpoint> outputSources,
            @Nullable String detail
    ) {
        public Rewrite {
            nodes = List.copyOf(nodes);
            internalEdges = List.copyOf(internalEdges);
            inputTargets = Map.copyOf(inputTargets);
            outputSources = Map.copyOf(outputSources);
            if (nodes.isEmpty()) {
                throw new IllegalArgumentException("A node rewrite needs at least one replacement");
            }
        }
    }

    /**
     * Explicit "cannot be unified automatically" report for one legacy node id. The node stays
     * untouched and compiles; the audit detail explains why automatic migration is not offered.
     */
    public interface NodeAudit {
        Identifier legacyId();

        /**
         * @return a machine-readable detail, or {@code null} when the node is not a legacy case
         */
        @Nullable String audit(ProgramGraph.Node node);
    }

    @FunctionalInterface
    public interface AuditFunction {
        @Nullable String audit(ProgramGraph.Node node);
    }

    public static NodeAudit audit(Identifier legacyId, AuditFunction function) {
        Objects.requireNonNull(legacyId, "legacyId");
        Objects.requireNonNull(function, "function");
        return new NodeAudit() {
            @Override
            public Identifier legacyId() {
                return legacyId;
            }

            @Override
            public @Nullable String audit(ProgramGraph.Node node) {
                return function.audit(node);
            }
        };
    }

    public record SchemaUpgrade(int fromVersion, UnaryOperator<JsonObject> configuration) {
        public SchemaUpgrade {
            if (fromVersion < 0) throw new IllegalArgumentException("fromVersion cannot be negative");
            Objects.requireNonNull(configuration, "configuration");
        }
    }

    public record Diagnostic(String code, int nodeId, String detail) {
        public static final String UNSUPPORTED_PROGRAM_SCHEMA = "unsupported_program_schema";
        public static final String UNSUPPORTED_NODE_SCHEMA = "unsupported_node_schema";
        public static final String REWRITE_FAILED = "rewrite_failed";
        public static final String LEGACY_ACTION_NOT_MIGRATED = "legacy_action_not_migrated";
        public static final String UNMAPPED_REWRITE_PORT = "unmapped_rewrite_port";

        public Diagnostic {
            Objects.requireNonNull(code, "code");
            detail = detail == null ? "" : detail;
        }
    }

    public record Result(
            AbilityProgram program,
            boolean changed,
            List<Diagnostic> diagnostics
    ) {
        public Result {
            Objects.requireNonNull(program, "program");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Result migrate(AbilityProgram program, ProgramNodeLookup lookup) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(lookup, "lookup");
        var diagnostics = new ArrayList<Diagnostic>();
        if (program.schemaVersion() != AbilityProgram.CURRENT_SCHEMA_VERSION) {
            diagnostics.add(new Diagnostic(
                    Diagnostic.UNSUPPORTED_PROGRAM_SCHEMA, -1,
                    "program_schema=" + program.schemaVersion()));
            return new Result(program, false, diagnostics);
        }
        var graph = program.graph();
        Map<Integer, org.academy.api.common.ability.program.ProgramEditorLayout.NodePosition> layout =
                new LinkedHashMap<>(program.editorLayout().nodePositions());
        var changed = false;
        for (var pass = 0; pass < MAX_REWRITE_PASSES; pass++) {
            var applied = false;
            for (var rewrite : rewrites) {
                var outcome = applyRewrite(graph, layout, rewrite, lookup, diagnostics);
                graph = outcome.graph;
                layout = outcome.layout;
                applied |= outcome.applied;
            }
            if (!applied) break;
            changed = true;
        }
        auditLegacyNodes(graph, diagnostics);
        var upgraded = applySchemaUpgrades(graph, lookup, diagnostics);
        graph = upgraded.graph;
        changed |= upgraded.changed;
        if (!changed) return new Result(program, false, diagnostics);
        return new Result(new AbilityProgram(
                program.schemaVersion(),
                program.id(),
                program.name(),
                program.category(),
                graph,
                new org.academy.api.common.ability.program.ProgramEditorLayout(layout)
        ), changed, diagnostics);
    }

    private record RewriteOutcome(
            ProgramGraph graph,
            Map<Integer, org.academy.api.common.ability.program.ProgramEditorLayout.NodePosition> layout,
            boolean applied
    ) {
    }

    private RewriteOutcome applyRewrite(
            ProgramGraph graph,
            Map<Integer, org.academy.api.common.ability.program.ProgramEditorLayout.NodePosition> layout,
            NodeRewrite rewrite,
            ProgramNodeLookup lookup,
            List<Diagnostic> diagnostics
    ) {
        var nodes = new ArrayList<>(graph.nodes());
        var edges = new ArrayList<>(graph.edges());
        var applied = false;
        for (var index = 0; index < nodes.size(); index++) {
            var node = nodes.get(index);
            if (!rewrite.legacyId().equals(node.type())) continue;
            var replacement = rewrite.rewrite(node, lookup);
            if (replacement == null) {
                var audit = audits.get(node.type());
                if (audit != null) {
                    var detail = audit.audit(node);
                    if (detail != null) {
                        report(diagnostics, new Diagnostic(
                                Diagnostic.LEGACY_ACTION_NOT_MIGRATED, node.id(), detail));
                        continue;
                    }
                }
                report(diagnostics, new Diagnostic(
                        Diagnostic.REWRITE_FAILED, node.id(), node.type().toString()));
                continue;
            }
            var idMap = allocateIds(nodes, replacement.nodes().size());
            var replacements = new ArrayList<ProgramGraph.Node>(replacement.nodes().size());
            for (var slot = 0; slot < replacement.nodes().size(); slot++) {
                var template = replacement.nodes().get(slot);
                replacements.add(new ProgramGraph.Node(
                        idMap.get(slot), template.type(), template.schemaVersion(),
                        template.configuration()));
            }
            for (var slot = 0; slot < replacement.internalEdges().size(); slot++) {
                var internal = replacement.internalEdges().get(slot);
                edges.add(new ProgramGraph.Edge(
                        remap(internal.from(), idMap),
                        remap(internal.to(), idMap)));
            }
            for (var edge : List.copyOf(edges)) {
                if (edge.from().nodeId() == node.id()) {
                    var source = replacement.outputSources().get(edge.from().port());
                    if (source == null) {
                        report(diagnostics, new Diagnostic(
                                Diagnostic.UNMAPPED_REWRITE_PORT, node.id(), edge.from().port()));
                        edges.remove(edge);
                        continue;
                    }
                    edges.remove(edge);
                    edges.add(new ProgramGraph.Edge(remap(source, idMap), edge.to()));
                } else if (edge.to().nodeId() == node.id()) {
                    var target = replacement.inputTargets().get(edge.to().port());
                    if (target == null) {
                        report(diagnostics, new Diagnostic(
                                Diagnostic.UNMAPPED_REWRITE_PORT, node.id(), edge.to().port()));
                        edges.remove(edge);
                        continue;
                    }
                    edges.remove(edge);
                    edges.add(new ProgramGraph.Edge(edge.from(), remap(target, idMap)));
                }
            }
            var position = layout.remove(node.id());
            for (var slot = 0; slot < replacements.size(); slot++) {
                var replacementNode = replacements.get(slot);
                if (position != null) {
                    layout.put(replacementNode.id(), new org.academy.api.common.ability.program
                            .ProgramEditorLayout.NodePosition(
                            position.x() + slot * REWRITE_NODE_SPACING, position.y()));
                }
            }
            nodes.remove(index);
            nodes.addAll(index, replacements);
            index += replacements.size() - 1;
            applied = true;
        }
        return applied ? new RewriteOutcome(new ProgramGraph(nodes, edges), layout, true)
                : new RewriteOutcome(graph, layout, false);
    }

    private void auditLegacyNodes(ProgramGraph graph, List<Diagnostic> diagnostics) {
        if (audits.isEmpty()) return;
        for (var node : graph.nodes()) {
            var audit = audits.get(node.type());
            if (audit == null) continue;
            var detail = audit.audit(node);
            if (detail == null) continue;
            report(diagnostics, new Diagnostic(
                    Diagnostic.LEGACY_ACTION_NOT_MIGRATED, node.id(), detail));
        }
    }

    /**
     * Records one diagnostic, ignoring exact repeats. Rewrite passes revisit unmigratable nodes,
     * so a graph with several legacy nodes must still report each condition exactly once.
     */
    private static void report(List<Diagnostic> diagnostics, Diagnostic diagnostic) {
        for (var existing : diagnostics) {
            if (existing.code().equals(diagnostic.code())
                    && existing.nodeId() == diagnostic.nodeId()
                    && existing.detail().equals(diagnostic.detail())) {
                return;
            }
        }
        diagnostics.add(diagnostic);
    }

    private static Map<Integer, Integer> allocateIds(List<ProgramGraph.Node> nodes, int count) {
        var next = 0;
        for (var node : nodes) next = Math.max(next, node.id() + 1);
        var map = new LinkedHashMap<Integer, Integer>();
        for (var slot = 0; slot < count; slot++) map.put(slot, next + slot);
        return map;
    }

    private static ProgramGraph.Endpoint remap(
            ProgramGraph.Endpoint endpoint,
            Map<Integer, Integer> idMap
    ) {
        var remapped = idMap.get(endpoint.nodeId());
        return remapped == null ? endpoint
                : new ProgramGraph.Endpoint(remapped, endpoint.port());
    }

    private record UpgradeOutcome(ProgramGraph graph, boolean changed) {
    }

    private UpgradeOutcome applySchemaUpgrades(
            ProgramGraph graph,
            ProgramNodeLookup lookup,
            List<Diagnostic> diagnostics
    ) {
        var nodes = new ArrayList<>(graph.nodes());
        var changed = false;
        for (var index = 0; index < nodes.size(); index++) {
            var node = nodes.get(index);
            var type = lookup.find(node.type());
            if (type == null || node.schemaVersion() == type.schemaVersion()) continue;
            if (node.schemaVersion() > type.schemaVersion()) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.UNSUPPORTED_NODE_SCHEMA, node.id(),
                        "node_schema=" + node.schemaVersion() + ":current=" + type.schemaVersion()));
                continue;
            }
            var configuration = node.configuration();
            if (!configuration.isJsonObject()) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.UNSUPPORTED_NODE_SCHEMA, node.id(),
                        "node_configuration_not_object"));
                continue;
            }
            var upgraded = configuration.getAsJsonObject().deepCopy();
            var version = node.schemaVersion();
            var complete = true;
            while (version < type.schemaVersion()) {
                var step = findUpgrade(node.type(), version);
                if (step == null) {
                    complete = false;
                    break;
                }
                upgraded = step.configuration().apply(upgraded);
                if (upgraded == null) {
                    complete = false;
                    break;
                }
                version = step.fromVersion() + 1;
            }
            if (!complete) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.UNSUPPORTED_NODE_SCHEMA, node.id(),
                        "node_schema=" + node.schemaVersion() + ":current=" + type.schemaVersion()));
                continue;
            }
            nodes.set(index, new ProgramGraph.Node(
                    node.id(), node.type(), type.schemaVersion(), upgraded));
            changed = true;
        }
        return changed ? new UpgradeOutcome(new ProgramGraph(nodes, graph.edges()), true)
                : new UpgradeOutcome(graph, false);
    }

    private @Nullable SchemaUpgrade findUpgrade(Identifier type, int version) {
        var upgrades = schemaUpgrades.get(type);
        if (upgrades == null) return null;
        for (var upgrade : upgrades) {
            if (upgrade.fromVersion() == version) return upgrade;
        }
        return null;
    }

    public static final class Builder {
        private final List<NodeRewrite> rewrites = new ArrayList<>();
        private final Map<Identifier, NodeAudit> audits = new HashMap<>();
        private final Map<Identifier, List<SchemaUpgrade>> upgrades = new HashMap<>();

        private Builder() {
        }

        public Builder rewrite(NodeRewrite rewrite) {
            rewrites.add(Objects.requireNonNull(rewrite, "rewrite"));
            return this;
        }

        public Builder audit(NodeAudit audit) {
            Objects.requireNonNull(audit, "audit");
            if (audits.putIfAbsent(audit.legacyId(), audit) != null) {
                throw new IllegalStateException("Duplicate node audit " + audit.legacyId());
            }
            return this;
        }

        public Builder schemaUpgrade(
                Identifier nodeType,
                int fromVersion,
                UnaryOperator<JsonObject> configuration
        ) {
            upgrades.computeIfAbsent(Objects.requireNonNull(nodeType, "nodeType"),
                    _ -> new ArrayList<>()).add(new SchemaUpgrade(fromVersion, configuration));
            return this;
        }

        public ProgramGraphMigrator build() {
            return new ProgramGraphMigrator(rewrites, audits, upgrades);
        }
    }
}
