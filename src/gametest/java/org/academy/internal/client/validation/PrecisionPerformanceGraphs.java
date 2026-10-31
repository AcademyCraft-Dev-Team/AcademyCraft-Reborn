package org.academy.internal.client.validation;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.internal.client.ability.program.ProgramTaskTemplates;
import org.academy.internal.common.ability.program.ProgramStructureMovementNodes;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic, ordinary valid graphs for the opt-in performance probes; never executes them. */
public final class PrecisionPerformanceGraphs {
    private PrecisionPerformanceGraphs() {}

    public static AbilityProgram editorGraph(int count) {
        var definition = AbilityProgramDefinitions.require(Identifier.parse("academy:accelerator"));
        var empty = program(definition, "editor-" + count, ProgramGraph.EMPTY, Map.of());
        if (count == 2) return ProgramTaskTemplates.create(empty, definition, capabilities(definition),
                ProgramTaskTemplates.Kind.ACCELERATOR_GUIDE_STRUCTURE);
        if (count != 32 && count != 128) throw new IllegalArgumentException("Expected 2, 32 or 128 nodes");
        var nodes = new ArrayList<ProgramGraph.Node>();
        var edges = new ArrayList<ProgramGraph.Edge>();
        var positions = new LinkedHashMap<Integer, ProgramEditorLayout.NodePosition>();
        nodes.add(node(definition, 0, BaseAbilityProgramDefinition.entryId(definition.category()), null));
        var output = new JsonObject();
        output.addProperty("value_type", "float");
        output.addProperty("text", "{value}");
        output.addProperty("audience", "self");
        nodes.add(node(definition, 1, CommonProgramNodeIds.DEBUG_OUTPUT, output));
        var scalar = new JsonObject(); scalar.addProperty("type", "float"); scalar.addProperty("value", "1.0");
        nodes.add(node(definition, 2, CommonProgramNodeIds.SCALAR_CONSTANT, scalar));
        var bool = new JsonObject(); bool.addProperty("type", "boolean"); bool.addProperty("value", "true");
        nodes.add(node(definition, 3, CommonProgramNodeIds.SCALAR_CONSTANT, bool));
        int previous = 2;
        String previousPort = "value";
        for (int id = 4; id < count; id++) {
            boolean select = id < 10;
            var config = new JsonObject();
            if (select) {
                config.addProperty("type", ProgramValueTypes.FLOAT.id().toString());
                nodes.add(node(definition, id, CommonProgramNodeIds.SELECT_VALUE, config));
                edges.add(edge(3, "value", id, "condition"));
                edges.add(edge(previous, previousPort, id, "when_true"));
                edges.add(edge(2, "value", id, "when_false"));
            } else {
                config.addProperty("type", "float"); config.addProperty("operator", "add");
                nodes.add(node(definition, id, CommonProgramNodeIds.NUMERIC_ARITHMETIC, config));
                edges.add(edge(previous, previousPort, id, "left"));
                edges.add(edge(2, "value", id, "right"));
            }
            previous = id;
            previousPort = select ? "value" : "result";
        }
        edges.add(edge(0, "flow", 1, "flow"));
        edges.add(edge(previous, previousPort, 1, "value"));
        for (int id = 0; id < count; id++) positions.put(id,
                new ProgramEditorLayout.NodePosition((id % 8) * 116, (id / 8) * 105));
        var result = program(definition, "editor-" + count, new ProgramGraph(nodes, edges), positions);
        requireValid(result);
        if (edges.size() != count * 2) throw new IllegalStateException("Dense graph missed its exact edge boundary");
        return result;
    }

    /** Reconstructs the historical ten-node movement topology before the optional seconds source. */
    public static AbilityProgram legacyTenGraph() {
        var definition = AbilityProgramDefinitions.require(Identifier.parse("academy:aeromanip"));
        var point = new JsonObject(); point.addProperty("dimension", "minecraft:overworld");
        point.addProperty("x", 0); point.addProperty("y", 80); point.addProperty("z", 0);
        var variable = new JsonObject(); variable.addProperty("name", "group_targets");
        variable.addProperty("type", ProgramValueTypes.STRUCTURE_SET.id().toString());
        var nodes = List.of(
                node(definition, 0, BaseAbilityProgramDefinition.entryId(definition.category()), null),
                node(definition, 1, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, point),
                node(definition, 2, CommonProgramNodeIds.FLOAT_CONSTANT, value(8)),
                node(definition, 3, CommonProgramNodeIds.STRUCTURES_AROUND, null),
                node(definition, 4, CommonProgramNodeIds.VARIABLE_SET, variable),
                node(definition, 5, CommonProgramNodeIds.VARIABLE_GET, variable),
                node(definition, 6, CommonProgramNodeIds.VECTOR_CONSTRUCT, null),
                node(definition, 7, CommonProgramNodeIds.FLOAT_CONSTANT, value(0)),
                node(definition, 9, CommonProgramNodeIds.FLOAT_CONSTANT, value(3)),
                node(definition, 8, Identifier.parse("academy:program/aeromanip/action/structure/move_group"),
                        ProgramStructureMovementNodes.defaults()));
        var edges = List.of(edge(0,"flow",4,"flow"), edge(1,"position",3,"center"),
                edge(2,"value",3,"radius"), edge(3,"structures",4,"value"), edge(4,"flow",8,"flow"),
                edge(5,"value",8,"structures"), edge(6,"vector",8,"displacement"),
                edge(7,"value",6,"x"), edge(7,"value",6,"y"), edge(9,"value",6,"z"));
        int[] order = {0, 1, 2, 3, 4, 5, 7, 9, 6, 8};
        var positions = new LinkedHashMap<Integer, ProgramEditorLayout.NodePosition>();
        for (int i = 0; i < order.length; i++) positions.put(order[i],
                new ProgramEditorLayout.NodePosition(i / 3 * 150, i % 3 * 90));
        var result = program(definition, "legacy-ten", new ProgramGraph(nodes, edges), positions);
        requireValid(result);
        return result;
    }

    public static Set<Identifier> capabilities(AbilityProgramDefinition definition) {
        var result = new HashSet<Identifier>();
        definition.editorCatalog().entries().forEach(entry -> result.addAll(entry.type().scope().requiredCapabilities()));
        return Set.copyOf(result);
    }

    private static AbilityProgram program(AbilityProgramDefinition definition, String name,
                                          ProgramGraph graph, Map<Integer, ProgramEditorLayout.NodePosition> positions) {
        return new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.nameUUIDFromBytes(("precision-performance/" + name).getBytes(StandardCharsets.UTF_8)),
                "Precision C " + name, definition.category(), graph, new ProgramEditorLayout(positions));
    }

    private static ProgramGraph.Node node(AbilityProgramDefinition definition, int id, Identifier type, JsonObject config) {
        var entry = Objects.requireNonNull(definition.editorCatalog().entry(type), type.toString());
        var configuration = entry.defaultConfiguration().getAsJsonObject();
        if (config != null) config.entrySet().forEach(field -> configuration.add(field.getKey(), field.getValue().deepCopy()));
        return new ProgramGraph.Node(id, type, entry.type().schemaVersion(), configuration);
    }

    private static void requireValid(AbilityProgram program) {
        var definition = AbilityProgramDefinitions.require(program.category());
        var compiled = definition.compile(program, capabilities(definition));
        if (!compiled.valid()) throw new IllegalStateException("Invalid performance graph: " + compiled.diagnostics());
    }

    private static JsonObject value(double value) { var result = new JsonObject(); result.addProperty("value", value); return result; }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
}
