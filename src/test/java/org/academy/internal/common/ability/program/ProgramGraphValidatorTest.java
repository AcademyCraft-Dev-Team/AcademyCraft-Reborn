package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramGraphValidatorTest {
    private static final Identifier MENTALOUT = AcademyCraft.academy("mentalout");
    private static final Identifier MELTDOWNER = AcademyCraft.academy("meltdowner");
    private static final Identifier CONTROL = AcademyCraft.academy("program_capability/mental_control");
    private static final Identifier ENTRY = AcademyCraft.academy("program_node/on_cast");
    private static final Identifier ACTION = AcademyCraft.academy("program_node/test_action");
    private static final Identifier LOOP = AcademyCraft.academy("program_node/test_loop");
    private static final Identifier INTEGER_PIPE = AcademyCraft.academy("program_node/integer_pipe");
    private static final Identifier BOOLEAN_VALUE = AcademyCraft.academy("program_node/boolean_value");

    @Test
    void acceptsCategoryAuthorizedProgram() {
        var types = baseTypes();
        var graph = new ProgramGraph(
                List.of(node(1, ENTRY), node(2, ACTION)),
                List.of(edge(1, "flow", 2, "flow"))
        );

        var result = validate(graph, types, MENTALOUT, Set.of(CONTROL));

        assertTrue(result.valid());
    }

    @Test
    void acceptsUniqueOpenActionFlowRootWithoutExplicitEntry() {
        var types = baseTypes();
        var graph = new ProgramGraph(
                List.of(node(2, ACTION)),
                List.of()
        );

        var result = validate(graph, types, MENTALOUT, Set.of(CONTROL));

        assertTrue(result.valid(), () -> result.diagnostics().toString());
    }

    @Test
    void rejectsAmbiguousOpenActionFlowRootsWithoutExplicitEntry() {
        var types = baseTypes();
        var graph = new ProgramGraph(
                List.of(node(2, ACTION), node(3, ACTION)),
                List.of()
        );

        var result = validate(graph, types, MENTALOUT, Set.of(CONTROL));

        assertFalse(result.valid());
        assertHas(result.diagnostics(), ProgramDiagnosticCode.NO_ENTRY);
    }

    @Test
    void reportsOpenActionFlowAsFlowTopologyInsteadOfMissingData() {
        var types = baseTypes();
        var graph = new ProgramGraph(
                List.of(node(1, ENTRY), node(2, ACTION)),
                List.of()
        );

        var result = validate(graph, types, MENTALOUT, Set.of(CONTROL));

        assertFalse(result.valid());
        assertHas(result.diagnostics(), ProgramDiagnosticCode.UNREACHABLE_FLOW_NODE);
        assertFalse(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == ProgramDiagnosticCode.MISSING_INPUT));
    }

    @Test
    void rejectsNodeFromAnotherCategoryAndMissingCapability() {
        var types = baseTypes();
        var graph = new ProgramGraph(
                List.of(node(1, ENTRY), node(2, ACTION)),
                List.of(edge(1, "flow", 2, "flow"))
        );

        var wrongCategory = validate(graph, types, MELTDOWNER, Set.of(CONTROL));
        var missingCapability = validate(graph, types, MENTALOUT, Set.of());

        assertHas(wrongCategory.diagnostics(), ProgramDiagnosticCode.CATEGORY_MISMATCH);
        assertHas(missingCapability.diagnostics(), ProgramDiagnosticCode.CAPABILITY_MISSING);
    }

    @Test
    void allowsControlFlowLoopsButRejectsDataCycles() {
        var types = baseTypes();
        var flowLoop = new ProgramGraph(
                List.of(node(1, ENTRY), node(2, LOOP), node(3, ACTION)),
                List.of(
                        edge(1, "flow", 2, "flow"),
                        edge(2, "body", 3, "flow"),
                        edge(3, "flow", 2, "flow")
                )
        );
        var dataCycle = new ProgramGraph(
                List.of(node(1, ENTRY), node(2, INTEGER_PIPE), node(3, INTEGER_PIPE)),
                List.of(
                        edge(2, "value", 3, "value"),
                        edge(3, "value", 2, "value")
                )
        );

        var flowResult = validate(flowLoop, types, MENTALOUT, Set.of(CONTROL));
        var dataResult = validate(dataCycle, types, MENTALOUT, Set.of(CONTROL));

        assertTrue(flowResult.valid());
        assertHas(dataResult.diagnostics(), ProgramDiagnosticCode.DATA_CYCLE);
    }

    @Test
    void reportsTypedPortAndRequiredInputErrors() {
        var types = baseTypes();
        var graph = new ProgramGraph(
                List.of(node(1, ENTRY), node(2, BOOLEAN_VALUE), node(3, INTEGER_PIPE)),
                List.of(edge(2, "value", 3, "value"))
        );

        var result = validate(graph, types, MENTALOUT, Set.of(CONTROL));

        assertFalse(result.valid());
        assertHas(result.diagnostics(), ProgramDiagnosticCode.TYPE_MISMATCH);
        assertHas(result.diagnostics(), ProgramDiagnosticCode.MISSING_INPUT);
    }

    private static Map<Identifier, ProgramNodeType<?>> baseTypes() {
        var types = new HashMap<Identifier, ProgramNodeType<?>>();
        types.put(ENTRY, type(
                ProgramNodeRole.ENTRY,
                ProgramNodeScope.COMMON,
                new ProgramNodeSchema(
                        List.of(),
                        List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
                )
        ));
        types.put(ACTION, type(
                ProgramNodeRole.ACTION,
                new ProgramNodeScope(Set.of(MENTALOUT), Set.of(CONTROL)),
                new ProgramNodeSchema(
                        List.of(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW)),
                        List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
                )
        ));
        types.put(LOOP, type(
                ProgramNodeRole.CONTROL,
                ProgramNodeScope.COMMON,
                new ProgramNodeSchema(
                        List.of(new ProgramPortDefinition("flow", ProgramValueTypes.FLOW, true, 2)),
                        List.of(
                                ProgramPortDefinition.output("body", ProgramValueTypes.FLOW),
                                ProgramPortDefinition.output("done", ProgramValueTypes.FLOW)
                        )
                )
        ));
        types.put(INTEGER_PIPE, type(
                ProgramNodeRole.VALUE,
                ProgramNodeScope.COMMON,
                new ProgramNodeSchema(
                        List.of(ProgramPortDefinition.requiredInput("value", ProgramValueTypes.INTEGER)),
                        List.of(ProgramPortDefinition.output("value", ProgramValueTypes.INTEGER))
                )
        ));
        types.put(BOOLEAN_VALUE, type(
                ProgramNodeRole.VALUE,
                ProgramNodeScope.COMMON,
                new ProgramNodeSchema(
                        List.of(),
                        List.of(ProgramPortDefinition.output("value", ProgramValueTypes.BOOLEAN))
                )
        ));
        return types;
    }

    private static ProgramNodeType<Unit> type(
            ProgramNodeRole role,
            ProgramNodeScope scope,
            ProgramNodeSchema schema
    ) {
        return new ProgramNodeType<>() {
            @Override
            public Codec<Unit> configurationCodec() {
                return MapCodec.unit(Unit.INSTANCE).codec();
            }

            @Override
            public int schemaVersion() {
                return 1;
            }

            @Override
            public ProgramNodeSchema schema(Unit configuration) {
                return schema;
            }

            @Override
            public ProgramNodeRole role() {
                return role;
            }

            @Override
            public ProgramNodePurity purity() {
                return role == ProgramNodeRole.ACTION
                        ? ProgramNodePurity.ACTION
                        : ProgramNodePurity.PURE;
            }

            @Override
            public ProgramNodeScope scope() {
                return scope;
            }
        };
    }

    private static ProgramGraph.Node node(int id, Identifier type) {
        return new ProgramGraph.Node(id, type, 1, new JsonObject());
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(from, output),
                new ProgramGraph.Endpoint(to, input)
        );
    }

    private static ProgramValidationResult validate(
            ProgramGraph graph,
            Map<Identifier, ProgramNodeType<?>> types,
            Identifier category,
            Set<Identifier> capabilities
    ) {
        return ProgramGraphValidator.validate(
                graph,
                new ProgramCompileContext(category, capabilities, ProgramLimits.DEFAULT),
                types::get
        );
    }

    private static void assertHas(
            List<ProgramDiagnostic> diagnostics,
            ProgramDiagnosticCode code
    ) {
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.code() == code),
                () -> "Expected " + code + " in " + diagnostics);
    }

    private enum Unit {
        INSTANCE
    }
}
