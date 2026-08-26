package org.academy.internal.common.ability.program;

import com.google.gson.JsonElement;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramCompileContext;
import org.academy.api.common.ability.program.ProgramDiagnostic;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramNodeSchema;
import org.academy.api.common.ability.program.ProgramValidationResult;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, named-port editing surface shared by all ability-program screens. */
public final class ProgramEditorDocument {
    private static final int MAX_NODE_ID = 1_000_000;

    private final AbilityProgram program;
    private final ProgramEditorNodeCatalog catalog;
    private final ProgramCompileContext context;

    public ProgramEditorDocument(
            AbilityProgram program,
            ProgramEditorNodeCatalog catalog,
            Set<net.minecraft.resources.Identifier> capabilities
    ) {
        this(program, catalog, capabilities, ProgramLimits.DEFAULT);
    }

    public ProgramEditorDocument(
            AbilityProgram program,
            AbilityProgramDefinition definition,
            Set<net.minecraft.resources.Identifier> capabilities
    ) {
        this(program, definition.editorCatalog(), capabilities, definition.limits());
        if (!program.category().equals(definition.category())) {
            throw new IllegalArgumentException("Program definition category does not match the program");
        }
    }

    private ProgramEditorDocument(
            AbilityProgram program,
            ProgramEditorNodeCatalog catalog,
            Set<net.minecraft.resources.Identifier> capabilities,
            ProgramLimits limits
    ) {
        if (program == null || catalog == null) {
            throw new IllegalArgumentException("Program editor document requires a program and catalog");
        }
        if (!program.category().equals(catalog.category())) {
            throw new IllegalArgumentException("Program editor catalog category does not match the program");
        }
        this.program = applyEditorDefaults(program, catalog);
        this.catalog = catalog;
        context = new ProgramCompileContext(
                this.program.category(),
                capabilities == null ? Set.of() : capabilities,
                limits
        );
    }

    /**
     * Enriches valid legacy configurations with newly introduced editor defaults. The raw values
     * always win, so opening an old graph cannot silently replace a player's explicit setting.
     */
    private static AbilityProgram applyEditorDefaults(
            AbilityProgram program,
            ProgramEditorNodeCatalog catalog
    ) {
        var changed = false;
        var nodes = new ArrayList<ProgramGraph.Node>(program.graph().nodes().size());
        for (var node : program.graph().nodes()) {
            var entry = catalog.entry(node.type());
            var raw = node.configuration();
            if (entry == null
                    || !raw.isJsonObject()
                    || !entry.defaultConfiguration().isJsonObject()
                    || catalog.schema(node.type(), raw) == null) {
                nodes.add(node);
                continue;
            }
            var normalized = catalog.normalizeConfiguration(node.type(), raw);
            if (normalized == null || !normalized.isJsonObject()) {
                nodes.add(node);
                continue;
            }
            var merged = entry.defaultConfiguration().getAsJsonObject().deepCopy();
            normalized.getAsJsonObject().entrySet().forEach(value ->
                    merged.add(value.getKey(), value.getValue().deepCopy()));
            if (merged.equals(raw) || catalog.schema(node.type(), merged) == null) {
                nodes.add(node);
                continue;
            }
            nodes.add(new ProgramGraph.Node(
                    node.id(), node.type(), node.schemaVersion(), merged));
            changed = true;
        }
        if (!changed) return program;
        return new AbilityProgram(
                program.schemaVersion(),
                program.id(),
                program.name(),
                program.category(),
                new ProgramGraph(nodes, program.graph().edges()),
                program.editorLayout()
        );
    }

    public AbilityProgram program() {
        return program;
    }

    public ProgramValidationResult validation() {
        return ProgramGraphValidator.validate(program.graph(), context, catalog);
    }

    public EditResult addNode(net.minecraft.resources.Identifier typeId, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return failure(ProgramDiagnosticCode.INVALID_NODE, -1, null);
        }
        if (program.graph().nodes().size() >= context.limits().maxNodes()) {
            return failure(ProgramDiagnosticCode.TOO_MANY_NODES, -1, null);
        }
        var entry = catalog.entry(typeId);
        if (entry == null) return failure(ProgramDiagnosticCode.UNKNOWN_NODE_TYPE, -1, null);
        if (!entry.type().scope().allowsCategory(context.category())) {
            return failure(ProgramDiagnosticCode.CATEGORY_MISMATCH, -1, null);
        }
        if (!context.capabilities().containsAll(entry.type().scope().requiredCapabilities())) {
            return failure(ProgramDiagnosticCode.CAPABILITY_MISSING, -1, null);
        }
        if (entry.type().role() == ProgramNodeRole.ENTRY) {
            var existingEntry = program.graph().nodes().stream()
                    .filter(node -> {
                        var existing = catalog.entry(node.type());
                        return existing != null
                                && existing.type().role() == ProgramNodeRole.ENTRY;
                    })
                    .findFirst()
                    .orElse(null);
            if (existingEntry != null) {
                return failure(
                        ProgramDiagnosticCode.MULTIPLE_ENTRIES,
                        existingEntry.id(),
                        null
                );
            }
        }
        var id = firstFreeNodeId();
        if (id < 0) return failure(ProgramDiagnosticCode.INVALID_NODE, -1, null);
        var nodes = new ArrayList<>(program.graph().nodes());
        nodes.add(new ProgramGraph.Node(
                id,
                typeId,
                entry.type().schemaVersion(),
                entry.defaultConfiguration()
        ));
        var positions = new HashMap<>(program.editorLayout().nodePositions());
        positions.put(id, new ProgramEditorLayout.NodePosition(x, y));
        return success(replace(new ProgramGraph(nodes, program.graph().edges()), positions));
    }

    public EditResult removeNode(int nodeId) {
        return removeNodes(Set.of(nodeId));
    }

    public EditResult removeNodes(Set<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return failure(ProgramDiagnosticCode.INVALID_NODE, -1, null);
        }
        for (var nodeId : nodeIds) {
            if (nodeId == null || node(nodeId) == null) {
                return failure(ProgramDiagnosticCode.INVALID_NODE,
                        nodeId == null ? -1 : nodeId, null);
            }
        }
        var nodes = program.graph().nodes().stream()
                .filter(node -> !nodeIds.contains(node.id())).toList();
        var edges = program.graph().edges().stream().filter(edge ->
                !nodeIds.contains(edge.from().nodeId())
                        && !nodeIds.contains(edge.to().nodeId())).toList();
        var positions = new HashMap<>(program.editorLayout().nodePositions());
        positions.keySet().removeAll(nodeIds);
        return success(replace(new ProgramGraph(nodes, edges), positions));
    }

    public EditResult moveNode(int nodeId, double x, double y) {
        if (node(nodeId) == null || !Double.isFinite(x) || !Double.isFinite(y)) {
            return failure(ProgramDiagnosticCode.INVALID_NODE, nodeId, null);
        }
        var positions = new HashMap<>(program.editorLayout().nodePositions());
        positions.put(nodeId, new ProgramEditorLayout.NodePosition(x, y));
        return success(replace(program.graph(), positions));
    }

    public EditResult translateNodes(Set<Integer> nodeIds, double deltaX, double deltaY) {
        if (nodeIds == null || nodeIds.isEmpty()
                || !Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
            return failure(ProgramDiagnosticCode.INVALID_NODE, -1, null);
        }
        for (var nodeId : nodeIds) {
            if (nodeId == null || node(nodeId) == null) {
                return failure(ProgramDiagnosticCode.INVALID_NODE,
                        nodeId == null ? -1 : nodeId, null);
            }
        }
        var positions = new HashMap<>(program.editorLayout().nodePositions());
        for (var nodeId : nodeIds) {
            var position = positions.get(nodeId);
            var x = (position == null ? 0.0 : position.x()) + deltaX;
            var y = (position == null ? 0.0 : position.y()) + deltaY;
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return failure(ProgramDiagnosticCode.INVALID_NODE, nodeId, null);
            }
            positions.put(nodeId, new ProgramEditorLayout.NodePosition(x, y));
        }
        return success(replace(program.graph(), positions));
    }

    public EditResult configureNode(int nodeId, JsonElement configuration) {
        var existing = node(nodeId);
        if (existing == null || configuration == null) {
            return failure(ProgramDiagnosticCode.INVALID_CONFIGURATION, nodeId, null);
        }
        var schema = catalog.schema(existing.type(), configuration);
        if (schema == null) {
            return failure(ProgramDiagnosticCode.INVALID_CONFIGURATION, nodeId, null);
        }
        var nodes = program.graph().nodes().stream().map(node -> node.id() == nodeId
                ? new ProgramGraph.Node(node.id(), node.type(), node.schemaVersion(), configuration)
                : node).toList();
        var graph = new ProgramGraph(nodes, program.graph().edges());
        var edgeDiagnostic = connectedEdgeDiagnostic(graph, nodeId, schema);
        return edgeDiagnostic == null
                ? success(replace(graph, program.editorLayout().nodePositions()))
                : new EditResult(null, edgeDiagnostic);
    }

    public EditResult connect(ProgramGraph.Endpoint from, ProgramGraph.Endpoint to) {
        if (from == null || to == null) return failure(ProgramDiagnosticCode.INVALID_EDGE, -1, null);
        var fromSchema = schema(from.nodeId());
        var toSchema = schema(to.nodeId());
        if (fromSchema == null || toSchema == null) {
            return failure(ProgramDiagnosticCode.INVALID_EDGE, to.nodeId(), to.port());
        }
        var output = fromSchema.output(from.port()).orElse(null);
        var input = toSchema.input(to.port()).orElse(null);
        if (output == null) return failure(ProgramDiagnosticCode.UNKNOWN_PORT, from.nodeId(), from.port());
        if (input == null) return failure(ProgramDiagnosticCode.UNKNOWN_PORT, to.nodeId(), to.port());
        if (!ProgramValueTypes.canConnect(output.type(), input.type())) {
            return failure(ProgramDiagnosticCode.TYPE_MISMATCH, to.nodeId(), to.port());
        }
        var edge = new ProgramGraph.Edge(from, to);
        if (program.graph().edges().contains(edge)) return success(this);
        if (program.graph().edges().size() >= context.limits().maxEdges()) {
            return failure(ProgramDiagnosticCode.TOO_MANY_EDGES, -1, null);
        }
        var inputCount = program.graph().edges().stream().filter(existing -> existing.to().equals(to)).count();
        var outputCount = program.graph().edges().stream().filter(existing -> existing.from().equals(from)).count();
        if (inputCount >= input.maxConnections()) {
            return failure(ProgramDiagnosticCode.TOO_MANY_CONNECTIONS, to.nodeId(), to.port());
        }
        if (outputCount >= output.maxConnections()) {
            return failure(ProgramDiagnosticCode.TOO_MANY_CONNECTIONS, from.nodeId(), from.port());
        }
        var edges = new ArrayList<>(program.graph().edges());
        edges.add(edge);
        var graph = new ProgramGraph(program.graph().nodes(), edges);
        if (!output.type().equals(ProgramValueTypes.FLOW) && hasDataCycle(graph)) {
            return failure(ProgramDiagnosticCode.DATA_CYCLE, to.nodeId(), to.port());
        }
        return success(replace(graph, program.editorLayout().nodePositions()));
    }

    public EditResult disconnect(ProgramGraph.Endpoint from, ProgramGraph.Endpoint to) {
        if (from == null || to == null) return failure(ProgramDiagnosticCode.INVALID_EDGE, -1, null);
        var edges = program.graph().edges().stream().filter(edge ->
                !edge.from().equals(from) || !edge.to().equals(to)).toList();
        return success(replace(
                new ProgramGraph(program.graph().nodes(), edges),
                program.editorLayout().nodePositions()
        ));
    }

    public EditResult rename(String name) {
        try {
            return success(new ProgramEditorDocument(new AbilityProgram(
                    program.schemaVersion(),
                    program.id(),
                    name,
                    program.category(),
                    program.graph(),
                    program.editorLayout()
            ), catalog, context.capabilities(), context.limits()));
        } catch (IllegalArgumentException exception) {
            return failure(ProgramDiagnosticCode.INVALID_CONFIGURATION, -1, null);
        }
    }

    private @Nullable ProgramDiagnostic connectedEdgeDiagnostic(
            ProgramGraph graph,
            int nodeId,
            ProgramNodeSchema changedSchema
    ) {
        for (var edge : graph.edges()) {
            if (edge.from().nodeId() != nodeId && edge.to().nodeId() != nodeId) continue;
            var fromSchema = edge.from().nodeId() == nodeId ? changedSchema : schema(edge.from().nodeId());
            var toSchema = edge.to().nodeId() == nodeId ? changedSchema : schema(edge.to().nodeId());
            if (fromSchema == null || toSchema == null) {
                return new ProgramDiagnostic(ProgramDiagnosticCode.INVALID_EDGE, nodeId, null);
            }
            var output = fromSchema.output(edge.from().port()).orElse(null);
            var input = toSchema.input(edge.to().port()).orElse(null);
            if (output == null || input == null) {
                return new ProgramDiagnostic(ProgramDiagnosticCode.UNKNOWN_PORT, nodeId,
                        output == null ? edge.from().port() : edge.to().port());
            }
            if (!ProgramValueTypes.canConnect(output.type(), input.type())) {
                return new ProgramDiagnostic(ProgramDiagnosticCode.TYPE_MISMATCH,
                        edge.to().nodeId(), edge.to().port());
            }
        }
        return null;
    }

    private boolean hasDataCycle(ProgramGraph graph) {
        var outgoing = new HashMap<Integer, List<Integer>>();
        for (var edge : graph.edges()) {
            var fromSchema = schema(graph, edge.from().nodeId());
            var output = fromSchema == null ? null : fromSchema.output(edge.from().port()).orElse(null);
            if (output == null || output.type().equals(ProgramValueTypes.FLOW)) continue;
            outgoing.computeIfAbsent(edge.from().nodeId(), _ -> new ArrayList<>())
                    .add(edge.to().nodeId());
        }
        var visiting = new HashSet<Integer>();
        var visited = new HashSet<Integer>();
        for (var node : graph.nodes()) {
            if (cycleFrom(node.id(), outgoing, visiting, visited)) return true;
        }
        return false;
    }

    private static boolean cycleFrom(
            int nodeId,
            Map<Integer, List<Integer>> outgoing,
            Set<Integer> visiting,
            Set<Integer> visited
    ) {
        if (visited.contains(nodeId)) return false;
        if (!visiting.add(nodeId)) return true;
        for (var target : outgoing.getOrDefault(nodeId, List.of())) {
            if (cycleFrom(target, outgoing, visiting, visited)) return true;
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private @Nullable ProgramNodeSchema schema(int nodeId) {
        return schema(program.graph(), nodeId);
    }

    private @Nullable ProgramNodeSchema schema(ProgramGraph graph, int nodeId) {
        var node = graph.nodes().stream().filter(candidate -> candidate.id() == nodeId)
                .findFirst().orElse(null);
        return node == null ? null : catalog.schema(node.type(), node.configuration());
    }

    private ProgramGraph.@Nullable Node node(int nodeId) {
        return program.graph().nodes().stream().filter(node -> node.id() == nodeId)
                .findFirst().orElse(null);
    }

    private int firstFreeNodeId() {
        var used = new HashSet<Integer>();
        program.graph().nodes().forEach(node -> used.add(node.id()));
        for (var id = 0; id <= MAX_NODE_ID; id++) {
            if (!used.contains(id)) return id;
        }
        return -1;
    }

    private ProgramEditorDocument replace(
            ProgramGraph graph,
            Map<Integer, ProgramEditorLayout.NodePosition> positions
    ) {
        return new ProgramEditorDocument(new AbilityProgram(
                program.schemaVersion(),
                program.id(),
                program.name(),
                program.category(),
                graph,
                new ProgramEditorLayout(positions)
        ), catalog, context.capabilities(), context.limits());
    }

    private static EditResult success(ProgramEditorDocument document) {
        return new EditResult(document, null);
    }

    private static EditResult failure(
            ProgramDiagnosticCode code,
            int nodeId,
            @Nullable String port
    ) {
        return new EditResult(null, new ProgramDiagnostic(code, nodeId, port));
    }

    public record EditResult(
            @Nullable ProgramEditorDocument document,
            @Nullable ProgramDiagnostic diagnostic
    ) {
        public EditResult {
            if ((document == null) == (diagnostic == null)) {
                throw new IllegalArgumentException("Program edit result needs exactly one outcome");
            }
        }

        public boolean successful() {
            return document != null;
        }

        public ProgramEditorDocument orElseThrow() {
            if (document == null) throw new IllegalStateException(String.valueOf(diagnostic));
            return document;
        }
    }
}
