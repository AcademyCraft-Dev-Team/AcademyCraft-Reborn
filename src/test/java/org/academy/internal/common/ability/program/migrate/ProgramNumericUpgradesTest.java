package org.academy.internal.common.ability.program.migrate;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.CommonProgramExecutors;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.registry.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramNumericUpgradesTest {
    private static final AbilityProgramDefinition DEFINITION = AbilityProgramDefinitions.mentalout();
    private static final Identifier CUSTOM = Identifier.parse("example:logic/integer/add");

    @Test
    void previewsAllTwentyNineExactMappingsWithoutChangingTheOriginalGraphOrRegistrations() {
        var source = allOperations();
        var encoded = ProgramBookCodec.encodeProgram(source);
        var plan = ProgramNumericUpgrades.preview(source, DEFINITION, Set.of());
        assertTrue(plan.canApply(), () -> plan.diagnostics().toString());
        assertEquals(29, plan.changes().size());
        assertEquals(1, plan.retained().size());
        assertEquals(CommonProgramNodeIds.INTEGER_DIVIDE, plan.retained().getFirst().type());
        assertEquals(ProgramNumericUpgrades.Reason.INTEGER_DIVISION_SEMANTICS, plan.retained().getFirst().reason());
        assertArrayEquals(encoded, ProgramBookCodec.encodeProgram(source));
        assertArrayEquals(encoded, ProgramBookCodec.encodeProgram(plan.source()));
        assertEquals(source.graph().edges(), plan.candidate().graph().edges());
        assertEquals(source.editorLayout(), plan.candidate().editorLayout());
        assertEquals(source.name(), plan.candidate().name());
        assertEquals(source.id(), plan.candidate().id());
        assertEquals(source.category(), plan.candidate().category());
        assertEquals(source.schemaVersion(), plan.candidate().schemaVersion());
        assertEquals(source.graph().nodes().stream().map(ProgramGraph.Node::id).toList(),
                plan.candidate().graph().nodes().stream().map(ProgramGraph.Node::id).toList());
        for (var change : plan.changes()) {
            var old = find(source, change.nodeId());
            var replacement = find(plan.candidate(), change.nodeId());
            var segments = old.type().getPath().split("/");
            var kind = segments[segments.length - 2];
            var operation = segments[segments.length - 1];
            var comparison = Set.of("equal", "less", "less_equal", "greater", "greater_equal").contains(operation);
            assertEquals(comparison ? CommonProgramNodeIds.NUMERIC_COMPARE : CommonProgramNodeIds.NUMERIC_ARITHMETIC,
                    replacement.type());
            assertEquals(old.type(), change.sourceType());
            assertEquals(replacement.type(), change.targetType());
            assertEquals(kind, change.configuration().getAsJsonObject().get("type").getAsString());
            assertEquals(operation, change.configuration().getAsJsonObject().get("operator").getAsString());
            assertEquals(change.configuration(), replacement.configuration());
            assertEquals(1, replacement.schemaVersion());
            assertSame(CommonProgramNodeCatalog.INSTANCE.find(old.type()), DEFINITION.nodeLookup().find(old.type()),
                    "Optional upgrades do not remove any old registered type");
            assertNotNull(CommonProgramExecutors.INSTANCE.find(old.type()));
            assertEquals(resolved(old), resolved(replacement), old.type().toString());
        }
    }

    @Test
    void allArithmeticAndComparisonModesRetainTheirLiteralStrongSchemaAndNoConfigurationInputs() {
        int arithmeticModes = 0, comparisonModes = 0;
        for (var kind : CommonProgramNodeCatalog.NumericKind.values()) {
            for (var operator : CommonProgramNodeCatalog.ArithmeticOperator.values()) {
                var json = configuration(kind.wireName(), operator.wireName());
                var config = CommonProgramNodeCatalog.NumericArithmeticConfiguration.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                var type = typed(CommonProgramNodeIds.NUMERIC_ARITHMETIC);
                var schema = resolved(type, json);
                var expectedInputs = operator == CommonProgramNodeCatalog.ArithmeticOperator.ABSOLUTE
                        ? List.of(ProgramPortDefinition.requiredInput("value", kind.type()))
                        : List.of(ProgramPortDefinition.requiredInput("left", kind.type()),
                                ProgramPortDefinition.requiredInput("right", kind.type()));
                assertEquals(expectedInputs, schema.inputs());
                assertEquals(List.of(ProgramPortDefinition.output("result", kind.type())), schema.outputs());
                assertEquals(List.of(), configurationPorts(type, json));
                assertEquals(config, CommonProgramNodeCatalog.NumericArithmeticConfiguration.CODEC.parse(JsonOps.INSTANCE,
                        CommonProgramNodeCatalog.NumericArithmeticConfiguration.CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow()).getOrThrow());
                arithmeticModes++;
            }
            for (var operator : CommonProgramNodeCatalog.ComparisonOperator.values()) {
                var json = configuration(kind.wireName(), operator.wireName());
                var type = typed(CommonProgramNodeIds.NUMERIC_COMPARE);
                var schema = resolved(type, json);
                assertEquals(List.of(ProgramPortDefinition.requiredInput("left", kind.type()),
                        ProgramPortDefinition.requiredInput("right", kind.type())), schema.inputs());
                assertEquals(List.of(ProgramPortDefinition.output("result", ProgramValueTypes.BOOLEAN)), schema.outputs());
                assertEquals(List.of(), configurationPorts(type, json));
                comparisonModes++;
            }
        }
        assertEquals(24, arithmeticModes, "Includes absolute, power and integer_divide in every numeric domain");
        assertEquals(15, comparisonModes);
    }

    @Test
    void integerDivisionAndAllFourLegacyConstantsAreDeliberatelyKept() {
        var source = singleOperation(CommonProgramNodeIds.INTEGER_DIVIDE, new JsonObject(), 1);
        var plan = ProgramNumericUpgrades.preview(source, DEFINITION, Set.of());
        assertFalse(plan.canApply());
        assertTrue(plan.changes().isEmpty());
        assertEquals(ProgramNumericUpgrades.Reason.INTEGER_DIVISION_SEMANTICS, plan.retained().getFirst().reason());
        assertArrayEquals(ProgramBookCodec.encodeProgram(source), ProgramBookCodec.encodeProgram(plan.candidate()));
        for (var id : List.of(CommonProgramNodeIds.BOOLEAN_CONSTANT, CommonProgramNodeIds.INTEGER_CONSTANT,
                CommonProgramNodeIds.BIG_INTEGER_CONSTANT, CommonProgramNodeIds.FLOAT_CONSTANT)) {
            var entry = DEFINITION.editorCatalog().entry(id);
            var graph = new ProgramGraph(List.of(entry(), stop(), new ProgramGraph.Node(7, id, 1, entry.defaultConfiguration())),
                    List.of(edge(1, "flow", 2, "flow")));
            var constantProgram = program(graph);
            var constants = ProgramNumericUpgrades.preview(constantProgram, DEFINITION, Set.of());
            assertTrue(constants.diagnostics().isEmpty());
            assertFalse(constants.canApply());
            assertTrue(constants.changes().isEmpty());
            assertArrayEquals(ProgramBookCodec.encodeProgram(constantProgram), ProgramBookCodec.encodeProgram(constants.candidate()));
        }
    }

    @Test
    void unknownFieldsFutureVersionsAndInvalidConfigurationsStayUntouched() {
        var unknown = new JsonObject(); unknown.addProperty("future_note", "must survive");
        var cases = List.of(
                new Rejection(singleOperation(CommonProgramNodeIds.INTEGER_ADD, unknown, 1),
                        ProgramNumericUpgrades.Reason.NONEMPTY_CONFIGURATION),
                new Rejection(singleOperation(CommonProgramNodeIds.INTEGER_ADD, new JsonObject(), 2),
                        ProgramNumericUpgrades.Reason.UNSUPPORTED_SCHEMA_VERSION),
                new Rejection(singleOperation(CommonProgramNodeIds.INTEGER_ADD, JsonNull.INSTANCE, 1),
                        ProgramNumericUpgrades.Reason.INVALID_CONFIGURATION)
        );
        for (var rejection : cases) {
            var plan = ProgramNumericUpgrades.preview(rejection.program(), DEFINITION, Set.of());
            assertFalse(plan.canApply());
            assertTrue(plan.changes().isEmpty());
            assertEquals(rejection.reason(), plan.retained().getFirst().reason());
            assertArrayEquals(ProgramBookCodec.encodeProgram(rejection.program()), ProgramBookCodec.encodeProgram(plan.candidate()));
        }
        var current = singleOperation(CommonProgramNodeIds.INTEGER_ADD, new JsonObject(), 1);
        var future = new AbilityProgram(2, current.id(), current.name(), current.category(), current.graph(), current.editorLayout());
        var futurePlan = ProgramNumericUpgrades.preview(future, DEFINITION, Set.of());
        assertFalse(futurePlan.canApply());
        assertEquals(ProgramNumericUpgrades.Reason.UNSUPPORTED_SCHEMA_VERSION, futurePlan.retained().getFirst().reason());
        assertArrayEquals(ProgramBookCodec.encodeProgram(future), ProgramBookCodec.encodeProgram(futurePlan.candidate()));
    }

    @Test
    void wholeGraphDiagnosticsBlockAnOtherwiseEligibleReplacement() {
        var valid = singleOperation(CommonProgramNodeIds.INTEGER_ADD, new JsonObject(), 1);
        var brokenEdges = valid.graph().edges().stream().filter(edge -> !edge.to().port().equals("left")).toList();
        var invalid = new AbilityProgram(valid.schemaVersion(), valid.id(), valid.name(), valid.category(),
                new ProgramGraph(valid.graph().nodes(), brokenEdges), valid.editorLayout());
        var plan = ProgramNumericUpgrades.preview(invalid, DEFINITION, Set.of());
        assertEquals(1, plan.changes().size());
        assertFalse(plan.canApply());
        assertTrue(plan.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code() == ProgramDiagnosticCode.MISSING_INPUT));
        assertEquals(brokenEdges, plan.candidate().graph().edges());
        var foreign = AbilityProgramDefinitions.require(AcademyCraft.academy("teleport"));
        assertFalse(ProgramNumericUpgrades.preview(valid, foreign, Set.of()).canApply());
    }

    @Test
    void customExecutorOverridesCannotBorrowTheBuiltinEquivalenceProof() {
        for (var replaced : List.of(CommonProgramNodeIds.INTEGER_ADD, CommonProgramNodeIds.NUMERIC_ARITHMETIC)) {
            var calls = new AtomicInteger();
            var definition = customDefinition(replaced, calls);
            var plan = ProgramNumericUpgrades.preview(singleOperation(CommonProgramNodeIds.INTEGER_ADD, new JsonObject(), 1),
                    definition, Set.of());
            assertFalse(plan.canApply());
            assertTrue(plan.changes().isEmpty());
            assertEquals(ProgramNumericUpgrades.Reason.EXTENSION_OR_CUSTOM_IMPLEMENTATION, plan.retained().getFirst().reason());
            assertEquals(0, calls.get(), "Preview never invokes a custom executor");
        }
    }

    @Test
    void unrelatedExtensionPayloadIsPreservedAndSnapshotsDefendAgainstJsonAndNumberMutation() {
        var calls = new AtomicInteger();
        var definition = customDefinition(null, calls);
        var mutable = new AtomicInteger(7);
        var extensionConfig = new JsonObject();
        extensionConfig.addProperty("number", mutable);
        var nested = new JsonObject(); nested.addProperty("value", "preserve"); extensionConfig.add("nested", nested);
        var source = singleOperation(CommonProgramNodeIds.INTEGER_ADD, new JsonObject(), 1);
        var nodes = new ArrayList<>(source.graph().nodes());
        nodes.add(new ProgramGraph.Node(99, CUSTOM, 1, extensionConfig));
        source = new AbilityProgram(source.schemaVersion(), source.id(), source.name(), source.category(),
                new ProgramGraph(nodes, source.graph().edges()), source.editorLayout());
        var encoded = ProgramBookCodec.encodeProgram(source);
        var plan = ProgramNumericUpgrades.preview(source, definition, Set.of());
        assertTrue(plan.canApply(), () -> plan.diagnostics().toString());
        assertArrayEquals(encoded, ProgramBookCodec.encodeProgram(plan.source()));
        assertEquals(find(source, 99), find(plan.candidate(), 99));
        mutable.set(17);
        find(plan.source(), 99).configuration().getAsJsonObject().getAsJsonObject("nested").addProperty("value", "changed");
        find(plan.candidate(), 99).configuration().getAsJsonObject().addProperty("number", 99);
        plan.changes().getFirst().configuration().getAsJsonObject().addProperty("operator", "divide");
        assertArrayEquals(encoded, ProgramBookCodec.encodeProgram(plan.source()));
        assertEquals(7, find(plan.candidate(), 99).configuration().getAsJsonObject().get("number").getAsInt());
        assertEquals("preserve", find(plan.source(), 99).configuration().getAsJsonObject().getAsJsonObject("nested").get("value").getAsString());
        assertEquals("add", plan.changes().getFirst().configuration().getAsJsonObject().get("operator").getAsString());
        assertThrows(UnsupportedOperationException.class, () -> plan.changes().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.retained().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.diagnostics().clear());
        assertEquals(0, calls.get());
    }

    @Test
    void previewIsNotAnAutomaticReadOrCompileMigrationAndIsIdempotentAfterApplication() {
        var source = singleOperation(CommonProgramNodeIds.INTEGER_ADD, new JsonObject(), 1);
        var before = ProgramBookCodec.encodeProgram(source);
        assertTrue(DEFINITION.compile(source, Set.of()).valid());
        var automatic = ProgramGraphMigrations.migrate(source, DEFINITION.nodeLookup()).program();
        assertArrayEquals(before, ProgramBookCodec.encodeProgram(automatic));
        var plan = ProgramNumericUpgrades.preview(source, DEFINITION, Set.of());
        assertTrue(plan.canApply());
        assertArrayEquals(before, ProgramBookCodec.encodeProgram(source));
        var next = ProgramNumericUpgrades.preview(plan.candidate(), DEFINITION, Set.of());
        assertFalse(next.canApply());
        assertTrue(next.changes().isEmpty());
        assertArrayEquals(ProgramBookCodec.encodeProgram(plan.candidate()), ProgramBookCodec.encodeProgram(next.candidate()));
    }

    private record Rejection(AbilityProgram program, ProgramNumericUpgrades.Reason reason) {}

    private static AbilityProgram allOperations() {
        var nodes = new ArrayList<ProgramGraph.Node>(List.of(entry(), stop()));
        var edges = new ArrayList<>(List.of(edge(1, "flow", 2, "flow")));
        int id = 10;
        for (var kind : List.of("integer", "big_integer", "float")) {
            int left = id++, right = id++;
            nodes.add(scalar(left, kind, "7")); nodes.add(scalar(right, kind, "3"));
            for (var operation : List.of("add", "subtract", "multiply", "divide", "modulo",
                    "equal", "less", "less_equal", "greater", "greater_equal")) {
                nodes.add(new ProgramGraph.Node(id, legacy(kind, operation), 1, new JsonObject()));
                edges.add(edge(left, "value", id, "left")); edges.add(edge(right, "value", id, "right"));
                id++;
            }
        }
        return program(new ProgramGraph(nodes, edges));
    }

    private static AbilityProgram singleOperation(Identifier id, com.google.gson.JsonElement raw, int version) {
        var nodes = List.of(entry(), stop(), scalar(3, "integer", "7"), scalar(4, "integer", "3"),
                new ProgramGraph.Node(5, id, version, raw));
        return program(new ProgramGraph(nodes, List.of(edge(1, "flow", 2, "flow"),
                edge(3, "value", 5, "left"), edge(4, "value", 5, "right"))));
    }

    private static ProgramGraph.Node entry() {
        return new ProgramGraph.Node(1, PrecisionProgramNodeIds.ON_CAST, 1, new JsonObject());
    }

    private static ProgramGraph.Node stop() {
        return new ProgramGraph.Node(2, CommonProgramNodeIds.STOP, 1, new JsonObject());
    }

    private static ProgramGraph.Node scalar(int id, String kind, String value) {
        var json = new JsonObject(); json.addProperty("type", kind); json.addProperty("value", value);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.SCALAR_CONSTANT, 1, json);
    }

    private static AbilityProgram program(ProgramGraph graph) {
        var positions = new LinkedHashMap<Integer, ProgramEditorLayout.NodePosition>();
        graph.nodes().forEach(node -> positions.put(node.id(), new ProgramEditorLayout.NodePosition(node.id() * 13.5, -node.id() * 2.25)));
        return new AbilityProgram(1, UUID.randomUUID(), "Keep numeric graph", DEFINITION.category(), graph, new ProgramEditorLayout(positions));
    }

    private static ProgramGraph.Node find(AbilityProgram program, int id) {
        return program.graph().nodes().stream().filter(node -> node.id() == id).findFirst().orElseThrow();
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }

    private static Identifier legacy(String kind, String operation) {
        return AcademyCraft.academy("program/core/logic/" + kind + "/" + operation);
    }

    private static JsonObject configuration(String kind, String operation) {
        var json = new JsonObject(); json.addProperty("type", kind); json.addProperty("operator", operation); return json;
    }

    private static ProgramNodeSchema resolved(ProgramGraph.Node node) {
        return resolved(typed(node.type()), node.configuration());
    }

    private static ProgramNodeType<?> typed(Identifier id) {
        return CommonProgramNodeCatalog.INSTANCE.find(id);
    }

    private static <C> ProgramNodeSchema resolved(ProgramNodeType<C> type, com.google.gson.JsonElement json) {
        return type.resolvedSchema(type.configurationCodec().parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    private static <C> List<ProgramConfigurationPort> configurationPorts(ProgramNodeType<C> type, com.google.gson.JsonElement json) {
        return type.configurationPorts(type.configurationCodec().parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    private static AbilityProgramDefinition customDefinition(Identifier override, AtomicInteger executions) {
        var extension = new UnrelatedExtension();
        var entryType = DEFINITION.nodeLookup().find(PrecisionProgramNodeIds.ON_CAST);
        var editor = ProgramEditorNodeCatalog.builder(DEFINITION.category()).includeCommonNodes()
                .add(PrecisionProgramNodeIds.ON_CAST, entryType, new JsonObject(), ProgramEditorNodeCatalog.Group.FLOW,
                        "test.entry", "test.port.", null)
                .add(CUSTOM, extension, new JsonObject(), ProgramEditorNodeCatalog.Group.VALUE,
                        "test.extension", "test.port.", null).build();
        return new AbilityProgramDefinition(DEFINITION.category(), Map.of(PrecisionProgramNodeIds.ON_CAST, entryType, CUSTOM, extension),
                id -> id.equals(CUSTOM) || id.equals(override) ? (_, _, _) -> {
                    executions.incrementAndGet();
                    return ProgramNodeStep.data(Map.of("value", new ProgramValue<>(ProgramValueTypes.INTEGER, 1)));
                } : null, editor, ProgramLimits.DEFAULT);
    }

    private static final class UnrelatedExtension implements ProgramNodeExtension<Boolean> {
        @Override public Codec<Boolean> configurationCodec() { return MapCodec.unit(false).codec(); }
        @Override public int schemaVersion() { return 1; }
        @Override public ProgramNodeRole role() { return ProgramNodeRole.VALUE; }
        @Override public ProgramNodePurity purity() { return ProgramNodePurity.PURE; }
        @Override public ProgramNodeScope scope() { return ProgramNodeScope.COMMON; }
        @Override public ProgramNodeSchema schema(Boolean configuration) {
            return new ProgramNodeSchema(List.of(), List.of(ProgramPortDefinition.output("value", ProgramValueTypes.INTEGER)));
        }
        @Override public ProgramNodeExecution<Boolean> execution() { return (_, _, _) -> ProgramNodeStep.data(Map.of()); }
        @Override public ProgramNodeEditorMetadata editorMetadata() {
            return new ProgramNodeEditorMetadata(new JsonObject(), ProgramNodeEditorMetadata.Group.VALUE, "test.extension", "test.port.");
        }
    }
}
