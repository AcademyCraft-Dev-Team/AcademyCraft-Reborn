package org.academy.internal.client.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Bounded, world-free explanation of the authored graph. It never evaluates a DATA node,
 * invokes an executor, acquires control, or estimates costs from client-side world state.
 * Callers may cache this immutable value for the document/catalog/capability revision.
 */
public final class ProgramTaskPreview {
    private ProgramTaskPreview() {}

    private static final String PREFIX = "screen.academy.program.preview.";
    private static final int MAX_NODES = 128, MAX_EDGES = 256, MAX_FIELDS = 16, MAX_TEXT = 160;

    public enum Source { CONNECTED, LITERAL, DEFAULT, REQUIRED_MISSING, UNSPECIFIED }

    public record Parameter(String name, String value) {}
    public record InputPreview(String port, ProgramValueType valueType, Source source,
                               List<ProgramGraph.Endpoint> from, String literal, String defaultKey) {
        public InputPreview { from = List.copyOf(from); }
    }
    public record NodePreview(int nodeId, Identifier nodeType, String titleKey, ProgramNodeRole role,
                              List<InputPreview> inputs, List<Parameter> configuration) {
        public NodePreview { inputs = List.copyOf(inputs); configuration = List.copyOf(configuration); }
    }
    public record Notice(String translationKey, int nodeId, List<String> arguments) {
        public Notice { arguments = List.copyOf(arguments); }
    }
    public record Preview(List<NodePreview> nodes, List<Notice> notices, int omittedNodes) {
        public Preview { nodes = List.copyOf(nodes); notices = List.copyOf(notices); }
    }

    public static Preview inspect(AbilityProgram program, AbilityProgramDefinition definition,
                                  Set<Identifier> capabilities) {
        Objects.requireNonNull(program, "program"); Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(capabilities, "capabilities");
        var graph = program.graph();
        var nodes = new LinkedHashMap<Integer, ProgramGraph.Node>();
        var notices = new ArrayList<Notice>();
        notice(notices, "read_only", -1);
        if (!program.category().equals(definition.category())) notice(notices, "wrong_category", -1);
        int count = Math.min(graph.nodes().size(), MAX_NODES);
        for (int index = 0; index < count; index++) {
            var node = graph.nodes().get(index);
            if (nodes.putIfAbsent(node.id(), node) != null) notice(notices, "duplicate_node", node.id());
        }
        var inputs = new HashMap<ProgramGraph.Endpoint, List<ProgramGraph.Endpoint>>();
        for (int index = 0; index < Math.min(graph.edges().size(), MAX_EDGES); index++) {
            var edge = graph.edges().get(index);
            inputs.computeIfAbsent(edge.to(), _ -> new ArrayList<>()).add(edge.from());
            if (!nodes.containsKey(edge.from().nodeId()) || !nodes.containsKey(edge.to().nodeId())) {
                notice(notices, "dangling_edge", edge.to().nodeId());
            }
        }
        if (graph.nodes().size() > MAX_NODES || graph.edges().size() > MAX_EDGES) notice(notices, "truncated", -1);
        var result = new ArrayList<NodePreview>();
        boolean actions = false, structures = false, mental = false, samples = false, look = false;
        for (var node : nodes.values()) {
            var entry = definition.editorCatalog().entry(node.type());
            if (entry == null) {
                notice(notices, "unknown_node", node.id(), node.type().toString());
                continue;
            }
            var decoded = decode(entry.type(), node.configuration());
            if (decoded == null) { notice(notices, "invalid_configuration", node.id()); continue; }
            if (!entry.type().scope().allowsCategory(program.category())) notice(notices, "wrong_category", node.id());
            for (var capability : entry.type().scope().requiredCapabilities()) {
                if (!capabilities.contains(capability)) notice(notices, "missing_capability", node.id(), capability.toString());
            }
            if (entry.presentationStatus() == ProgramEditorNodeCatalog.PresentationStatus.UNRELEASED) {
                notice(notices, "unreleased", node.id());
            }
            var configuration = node.configuration().isJsonObject() ? node.configuration().getAsJsonObject() : new JsonObject();
            var nodeInputs = new ArrayList<InputPreview>();
            for (var port : decoded.schema.inputs()) {
                if (port.type().equals(ProgramValueTypes.FLOW)) continue;
                var from = inputs.getOrDefault(new ProgramGraph.Endpoint(node.id(), port.name()), List.of());
                if (!from.isEmpty()) {
                    var source = nodes.get(from.getFirst().nodeId());
                    String literal = from.size() == 1 ? literal(source, from.getFirst().port(), inputs, definition) : "";
                    nodeInputs.add(new InputPreview(port.name(), port.type(), literal.isEmpty() ? Source.CONNECTED : Source.LITERAL,
                            from, literal, ""));
                } else if (decoded.configurationFields.contains(port.name()) && configuration.has(port.name())) {
                    nodeInputs.add(new InputPreview(port.name(), port.type(), Source.DEFAULT, List.of(),
                            text(configuration.get(port.name())), PREFIX + "default.configuration"));
                } else {
                    var fallback = fallback(node, configuration, port.name(), inputs);
                    nodeInputs.add(new InputPreview(port.name(), port.type(), !fallback.key.isEmpty() ? Source.DEFAULT
                            : port.required() ? Source.REQUIRED_MISSING : Source.UNSPECIFIED,
                            List.of(), fallback.literal, fallback.key));
                }
            }
            var parameters = configuration.entrySet().stream().limit(MAX_FIELDS)
                    .map(field -> new Parameter(field.getKey(), text(field.getValue()))).toList();
            result.add(new NodePreview(node.id(), node.type(), entry.translationKey(), entry.type().role(), nodeInputs, parameters));
            actions |= entry.type().role() == ProgramNodeRole.ACTION;
            structures |= structureAction(node.type());
            mental |= exactPath(node.type(), "program/mentalout/action/guard_mode")
                    || exactPath(node.type(), "program/mentalout/action/path_to");
            samples |= node.type().equals(CommonProgramNodeIds.SAMPLE_VALUE);
            look |= node.type().equals(CommonProgramNodeIds.LOOK_TARGET) || node.type().equals(CommonProgramNodeIds.LOOK_STRUCTURE);
        }
        if (actions) { notice(notices, "server_checks", -1); notice(notices, "cost_runtime", -1); }
        if (structures) notice(notices, "structure_motion", -1);
        if (mental) notice(notices, "committed_mental_control", -1);
        if (samples) notice(notices, "sample_identity", -1);
        if (look) notice(notices, "look_miss", -1);
        return new Preview(result, notices, graph.nodes().size() - count);
    }

    private record Decoded(ProgramNodeSchema schema, Set<String> configurationFields) {}
    private static <C> @Nullable Decoded decode(ProgramNodeType<C> type, JsonElement json) {
        try {
            var config = type.configurationCodec().parse(JsonOps.INSTANCE, json).result().orElse(null);
            if (config == null) return null;
            var fields = new HashSet<String>();
            type.configurationPorts(config).forEach(port -> fields.add(port.field()));
            return new Decoded(type.resolvedSchema(config), Set.copyOf(fields));
        } catch (RuntimeException invalid) { return null; }
    }

    private static String literal(ProgramGraph.@Nullable Node node, String output,
                                  Map<ProgramGraph.Endpoint, List<ProgramGraph.Endpoint>> inputs,
                                  AbilityProgramDefinition definition) {
        if (node == null) return "";
        if (!output.equals("value") || inputs.containsKey(new ProgramGraph.Endpoint(node.id(), "value"))) return "";
        var id = node.type();
        if (!Set.of(CommonProgramNodeIds.SCALAR_CONSTANT, CommonProgramNodeIds.BOOLEAN_CONSTANT,
                CommonProgramNodeIds.INTEGER_CONSTANT, CommonProgramNodeIds.FLOAT_CONSTANT,
                CommonProgramNodeIds.BIG_INTEGER_CONSTANT, CommonProgramNodeIds.TEXT_CONSTANT).contains(id)) return "";
        var entry = definition.editorCatalog().entry(id);
        if (entry == null || decode(entry.type(), node.configuration()) == null) return "";
        var config = node.configuration();
        return config.isJsonObject() && config.getAsJsonObject().has("value")
                ? text(config.getAsJsonObject().get("value")) : "";
    }

    private record Fallback(String key, String literal) {}
    private static Fallback fallback(ProgramGraph.Node node, JsonObject config, String port,
                                     Map<ProgramGraph.Endpoint, List<ProgramGraph.Endpoint>> inputs) {
        var id = node.type();
        boolean groupMove = exactPath(id, "program/accelerator/action/structure/move_group")
                || exactPath(id, "program/electromaster/action/structure/move_group")
                || exactPath(id, "program/aeromanip/action/structure/move_group");
        if (groupMove) {
            boolean hasGroup = inputs.containsKey(new ProgramGraph.Endpoint(node.id(), "structures"));
            boolean hasSingle = inputs.containsKey(new ProgramGraph.Endpoint(node.id(), "structure"));
            if (port.equals("structure") && !hasGroup && !hasSingle) return defaultValue("look_structure", "");
            if (port.equals("displacement")) return defaultValue("view_displacement", "3");
            if (port.equals("seconds") && config.has("duration_seconds")) {
                return defaultValue("configuration", text(config.get("duration_seconds")));
            }
        }
        if (exactPath(id, "program/meltdowner/action/mining_beam") || exactPath(id, "program/meltdowner/action/electron_beam")) {
            if (port.equals("origin")) return defaultValue("caster_eyes", "");
            if (port.equals("direction") && exactPath(id, "program/meltdowner/action/mining_beam")) {
                return defaultValue("look_direction", "");
            }
        }
        return new Fallback("", "");
    }

    private static Fallback defaultValue(String suffix, String literal) { return new Fallback(PREFIX + "default." + suffix, literal); }
    private static boolean exactPath(Identifier id, String path) { return id.getNamespace().equals("academy") && id.getPath().equals(path); }
    private static boolean structureAction(Identifier id) {
        return id.getNamespace().equals("academy") && (id.getPath().startsWith("program/accelerator/action/structure/")
                || id.getPath().startsWith("program/electromaster/action/structure/")
                || id.getPath().startsWith("program/aeromanip/action/structure/"));
    }
    private static String text(JsonElement value) {
        String text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString();
        return text.codePointCount(0, text.length()) <= MAX_TEXT ? text
                : text.substring(0, text.offsetByCodePoints(0, MAX_TEXT)) + "…";
    }
    private static void notice(List<Notice> target, String suffix, int nodeId, String... args) {
        target.add(new Notice(PREFIX + suffix, nodeId, List.of(args)));
    }
}
