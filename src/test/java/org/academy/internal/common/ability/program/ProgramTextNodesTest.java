package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeCatalog;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.ProgramNodeExecutor;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProgramTextNodesTest {
    private static final ProgramWorldPosition ORIGIN = new ProgramWorldPosition(
            Identifier.parse("minecraft:the_nether"), 10, 64, -20);

    @Test
    void chatSplitParseAndBranchExecuteTogetherAndInvalidInputDoesNotAct() {
        var definition = AbilityProgramDefinitions.mentalout();
        var ids = List.of(CommonProgramNodeIds.TRIGGER_CHAT, CommonProgramNodeIds.CHAT_TRIGGER_MESSAGE,
                CommonProgramNodeIds.TEXT_SPLIT, CommonProgramNodeIds.TEXT_TO_VEC3,
                CommonProgramNodeIds.BRANCH, CommonProgramNodeIds.VARIABLE_SET);
        var nodes = new ArrayList<ProgramGraph.Node>();
        for (var id : ids) {
            var configuration = definition.editorCatalog().entry(id).defaultConfiguration().getAsJsonObject();
            if (id.equals(CommonProgramNodeIds.TEXT_SPLIT)) {
                configuration.addProperty("mode", "delimiter");
                configuration.addProperty("delimiter", "|");
                configuration.addProperty("fragment_index", 1);
            }
            if (id.equals(CommonProgramNodeIds.VARIABLE_SET)) {
                configuration.addProperty("name", "destination");
                configuration.addProperty("type", ProgramValueTypes.WORLD_POSITION.id().toString());
            }
            nodes.add(new ProgramGraph.Node(nodes.size(), id,
                    definition.editorCatalog().entry(id).type().schemaVersion(), configuration));
        }
        var graph = new ProgramGraph(nodes, List.of(edge(0, "flow", 4, "flow"),
                edge(1, "text", 2, "text"), edge(2, "text", 3, "text"),
                edge(3, "success", 4, "condition"), edge(4, "true", 5, "flow"),
                edge(3, "result", 5, "value")));
        var compiled = definition.compile(graph, Set.of());
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        for (var message : List.of("前进|^ ^ ^5|确认", "前进|格式错误|确认")) {
            var session = new ProgramVm.Session(compiled.program());
            var invocation = new ProgramInvocationContext(UUID.randomUUID(), 0,
                    ProgramTriggers.Type.CHAT, null, 0, null, null, null, message);
            var frame = new ProgramExecutionFrame(ProgramActionTransaction.sequential(), resolver(), invocation, null);
            var result = session.run(0, 100, definition.executors(), frame);
            assertEquals(ProgramVmResult.Status.COMPLETED, result.status(), result.toString());
            if (message.contains("^")) {
                assertEquals(new ProgramWorldPosition(ORIGIN.dimension(), 10, 64, -15),
                        session.variables().get("destination").value());
            } else assertFalse(session.variables().containsKey("destination"));
        }
    }

    @Test
    void dynamicIndexOverridesConfigurationAndEmptyFragmentsHaveAnExistenceSignal() {
        var configuration = new CommonProgramNodeCatalog.TextSplitConfiguration("delimiter", "|", 0, true, false);
        var step = execute(CommonProgramNodeIds.TEXT_SPLIT, configuration, null,
                Map.of("text", text("A||C"), "fragment_index", integer(1)));
        assertEquals("", step.outputs().get("text").value());
        assertEquals(3, step.outputs().get("count").value());
        assertEquals(true, step.outputs().get("exists").value());
        var missing = execute(CommonProgramNodeIds.TEXT_SPLIT, configuration, null,
                Map.of("text", text("A||C"), "fragment_index", integer(-1)));
        assertEquals(false, missing.outputs().get("exists").value());
    }

    @Test
    void directionIsNormalizedWithoutOverflowAndZeroOrMalformedInputHasNoResult() {
        var configuration = new CommonProgramNodeCatalog.TextToVec3Configuration(
                CommonProgramNodeCatalog.Vec3Kind.DIRECTION, "exact", 0);
        for (var input : List.of("0 0 7", "0 0 1e308", "0 0 1e-300")) {
            var step = execute(CommonProgramNodeIds.TEXT_TO_VEC3, configuration, null, Map.of("text", text(input)));
            assertEquals(new ProgramDirection(0, 0, 1), step.outputs().get("result").value());
            assertEquals(true, step.outputs().get("success").value());
        }
        for (var input : List.of("0 0 0", "1e309 0 1", "~ ~ ~", "^1 ~2 ^3")) {
            var step = execute(CommonProgramNodeIds.TEXT_TO_VEC3, configuration, null, Map.of("text", text(input)));
            assertEquals(false, step.outputs().get("success").value());
            assertFalse(step.outputs().containsKey("result"));
        }
    }

    @Test
    void explicitReferenceAndMatchIndexOverrideCasterAndDefaultIndex() {
        var frame = new ProgramExecutionFrame(ProgramActionTransaction.sequential(), resolver());
        var context = new ProgramVmContext(0, new HashMap<>(), new HashMap<>(), frame);
        var configuration = new CommonProgramNodeCatalog.TextToVec3Configuration(
                CommonProgramNodeCatalog.Vec3Kind.WORLD_POSITION, "extract", 0);
        var step = execute(CommonProgramNodeIds.TEXT_TO_VEC3, configuration, context, Map.of(
                "text", text("(1 2 3) 然后 (~ ~1 ~)"), "match_index", integer(1),
                "reference", new ProgramValue<>(ProgramValueTypes.ENTITY_REFERENCE, "other")));
        assertEquals(new ProgramWorldPosition(ORIGIN.dimension(), 100, 71, 200), step.outputs().get("result").value());
    }

    private static ProgramTargetResolver resolver() {
        return new ProgramTargetResolver() {
            public Object caster() { return "caster"; }
            public Optional<ProgramWorldPosition> positionOf(Object reference) {
                return Optional.of(reference.equals("other")
                        ? new ProgramWorldPosition(ORIGIN.dimension(), 100, 70, 200) : ORIGIN);
            }
            public Optional<ProgramDirection> lookDirectionOf(Object reference) {
                return Optional.of(new ProgramDirection(0, 0, 1));
            }
            public List<?> entitiesAround(ProgramWorldPosition center, double radius) { return List.of(); }
            public Optional<ProgramBlockPosition> raycastBlock(ProgramWorldPosition origin,
                    ProgramDirection direction, double distance) { return Optional.empty(); }
        };
    }

    @SuppressWarnings("unchecked")
    private static <C> ProgramNodeStep execute(Identifier id, C configuration, ProgramVmContext context,
                                               Map<String, ProgramValue<?>> values) {
        var inputs = new HashMap<String, List<ProgramValue<?>>>();
        values.forEach((key, value) -> inputs.put(key, List.of(value)));
        return ((ProgramNodeExecutor<C>) CommonProgramExecutors.INSTANCE.find(id))
                .execute(context, configuration, new ProgramInputView(inputs));
    }

    private static ProgramValue<?> text(String value) { return new ProgramValue<>(ProgramValueTypes.TEXT, value); }
    private static ProgramValue<?> integer(int value) { return new ProgramValue<>(ProgramValueTypes.INTEGER, value); }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
}
