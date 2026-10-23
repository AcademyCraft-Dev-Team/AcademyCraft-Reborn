package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramCapabilities;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramCapabilities;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramCapabilities;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProgramStructureOrientationNodesTest {
    private static final List<String> CATEGORIES = List.of("accelerator", "electromaster", "aeromanip");
    private static final ProgramStructureControl CONTROL = new ProgramStructureControl(new StructureRef(
            UUID.randomUUID(), Identifier.parse("minecraft:overworld"), Identifier.parse("academy:test"), 1),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test
    void allCategoriesKeepLegacyRotateAndExposeBoundedNewNodeSchemas() {
        for (var category : CATEGORIES) {
            var definition = definition(category);
            var rotate = type(definition, id(category, "rotate"));
            var configuration = rotate.configurationCodec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
            assertEquals(1, rotate.schemaVersion());
            assertEquals(List.of("flow", "control", "pose"), rotate.resolvedSchema(configuration).inputs().stream()
                    .map(ProgramPortDefinition::name).toList());
            assertTrue(rotate.configurationPorts(configuration).isEmpty());
            for (var action : List.of("reset_orientation", "spin")) {
                var nodeId = id(category, action);
                var node = type(definition, nodeId);
                var defaults = definition.editorCatalog().entry(nodeId).defaultConfiguration();
                var decoded = node.configurationCodec().parse(JsonOps.INSTANCE, defaults).getOrThrow();
                assertEquals(1, node.schemaVersion());
                assertNotNull(definition.executors().find(nodeId));
                assertEquals(ProgramValueTypes.STRUCTURE_CONTROL,
                        node.resolvedSchema(decoded).input("control").orElseThrow().type());
                assertEquals(List.of("result", "flow"), node.resolvedSchema(decoded).outputs().stream()
                        .map(ProgramPortDefinition::name).toList());
            }
        }
    }

    @Test
    void codecsRejectNonFiniteInputsUnknownModesAndZeroSpinInsteadOfSilentlyDefaulting() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0.0, 100.01}) {
            var input = ProgramStructureOrientationNodes.spinDefaults();
            input.addProperty("angular_speed", value);
            assertTrue(ProgramStructureOrientationNodes.SpinConfiguration.CODEC.parse(JsonOps.INSTANCE, input).error().isPresent());
        }
        for (double value : new double[]{Double.NaN, -1.0, 0.0, 60.01}) {
            var input = ProgramStructureOrientationNodes.resetDefaults();
            input.addProperty("timeout_seconds", value);
            assertTrue(ProgramStructureOrientationNodes.ResetConfiguration.CODEC.parse(JsonOps.INSTANCE, input).error().isPresent());
        }
        var badFrame = ProgramStructureOrientationNodes.spinDefaults();
        badFrame.addProperty("frame", "camera");
        assertTrue(ProgramStructureOrientationNodes.SpinConfiguration.CODEC.parse(JsonOps.INSTANCE, badFrame).error().isPresent());
        var reverse = ProgramStructureOrientationNodes.spinDefaults();
        reverse.addProperty("frame", "local");
        reverse.addProperty("angular_speed", -2.5);
        reverse.addProperty("duration_seconds", 0.051);
        var request = ProgramStructureOrientationNodes.SpinConfiguration.CODEC.parse(JsonOps.INSTANCE, reverse)
                .getOrThrow().request(new ProgramDirection(0, 1, 0));
        assertEquals(StructureOrientation.AxisFrame.LOCAL, request.frame());
        assertEquals(-2.5, request.angularSpeed());
        assertEquals(2, request.durationTicks(), "fractional ticks round up, never silently become zero");
    }

    @Test
    void oldAndExtendedGraphsRoundTripAndCompileWithoutChangingLegacyNodeData() {
        for (var category : CATEGORIES) {
            for (boolean extended : List.of(false, true)) {
                var graph = graph(category, extended);
                var program = new AbilityProgram(1, UUID.randomUUID(), "orientation compatibility",
                        Identifier.parse("academy:" + category), graph, ProgramEditorLayout.EMPTY);
                var decoded = ProgramBookCodec.decodeProgram(ProgramBookCodec.encodeProgram(program));
                assertTrue(decoded.valid());
                assertEquals(program, decoded.program());
                var compiled = definition(category).compile(decoded.program().graph(), Set.of(capability(category)));
                assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
                assertEquals(new JsonObject(), decoded.program().graph().nodes().stream()
                        .filter(node -> node.id() == 4).findFirst().orElseThrow().configuration());
            }
        }
    }

    @Test
    void registeredExecutorsSubmitOncePollTheirReceiptAndClearItOnCompletion() {
        for (var category : CATEGORIES) {
            for (var action : List.of("rotate", "reset_orientation", "spin")) {
                var runtime = new ScriptedRuntime();
                var state = new HashMap<String, Object>();
                var context = context(runtime, state);
                var nodeId = id(category, action);
                context.enterNode(7, nodeId);
                var executor = executor(definition(category), nodeId);
                var configuration = configuration(category, nodeId);
                var first = executor.execute(context, configuration, inputs(CONTROL));
                assertEquals(ProgramNodeStep.Directive.RETRY, first.directive());
                assertTrue(first.outputs().isEmpty());
                assertEquals(1, runtime.submissions);
                assertEquals(0, runtime.polls);
                assertEquals(ProgramNodeStep.Directive.RETRY,
                        executor.execute(context, configuration, inputs(CONTROL)).directive());
                var done = executor.execute(context, configuration, inputs(CONTROL));
                assertEquals(ProgramNodeStep.Directive.CONTINUE, done.directive());
                assertEquals(ProgramStructureOperationResult.Status.COMPLETED,
                        ((ProgramStructureOperationResult) done.outputs().get("result").value()).status());
                assertEquals(1, runtime.submissions);
                assertEquals(2, runtime.polls);
                assertTrue(state.isEmpty());
                executor.execute(context, configuration, inputs(CONTROL));
                assertEquals(2, runtime.submissions, "a later visit creates a fresh operation");
            }
        }
    }

    @Test
    void legacyRotateForwardsTheAbsoluteSignedPoseAndKeepsTheOptionalPort() {
        var pose = new ProgramStructurePose(new ProgramDirection(1, 2, 3), -Math.PI * 0.75);
        for (var category : CATEGORIES) {
            for (boolean supplied : List.of(false, true)) {
                var runtime = new ScriptedRuntime();
                var context = context(runtime, new HashMap<>());
                var nodeId = id(category, "rotate");
                context.enterNode(4, nodeId);
                var values = new HashMap<String, List<ProgramValue<?>>>();
                values.put("control", List.of(new ProgramValue<>(ProgramValueTypes.STRUCTURE_CONTROL, CONTROL)));
                if (supplied) values.put("pose", List.of(new ProgramValue<>(ProgramValueTypes.STRUCTURE_POSE, pose)));
                assertEquals(ProgramNodeStep.Directive.RETRY, executor(definition(category), nodeId)
                        .execute(context, configuration(category, nodeId), new ProgramInputView(values)).directive());
                assertEquals(supplied ? pose : null, runtime.pose);
            }
        }
    }

    @Test
    void vmSuspendsAtTheActionWithoutBusyPollingOrPublishingDownstreamFlow() {
        for (var category : CATEGORIES) {
            var definition = definition(category);
            var compiled = definition.compile(graph(category, true), Set.of(capability(category)));
            assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
            var runtime = new ScriptedRuntime();
            var frame = new ProgramExecutionFrame(new ProgramActionTransaction(), runtime);
            var session = new ProgramVm.Session(compiled.program());
            ProgramExecutorLookup lookup = nodeId -> {
                if (nodeId.equals(id(category, "extract"))) return (_, _, _) -> ProgramNodeStep.next("flow", Map.of(
                        "structure", new ProgramValue<>(ProgramValueTypes.STRUCTURE_REFERENCE,
                                new ProgramStructureReference(CONTROL.reference(), CONTROL.structureRuntimeId())),
                        "result", result()));
                if (nodeId.equals(id(category, "control"))) return (_, _, _) -> ProgramNodeStep.next("flow",
                        Map.of("control", new ProgramValue<>(ProgramValueTypes.STRUCTURE_CONTROL, CONTROL), "result", result()));
                return definition.executors().find(nodeId);
            };
            var pending = session.run(10, 1000, lookup, frame);
            assertEquals(ProgramVmResult.Status.SUSPENDED, pending.status());
            assertEquals(4, pending.nodeId(), "legacy rotate now awaits its real angular receipt");
            session.run(10, 1000, lookup, frame);
            assertEquals(1, runtime.submissions);
            assertEquals(0, runtime.polls, "RETRY must not busy-poll in the same tick");
            session.run(11, 1000, lookup, frame);
            assertEquals(1, runtime.submissions);
            var resetting = session.run(12, 1000, lookup, frame);
            assertEquals(5, resetting.nodeId(), "reset starts only after legacy rotate completed");
            assertEquals(2, runtime.submissions);
            session.run(13, 1000, lookup, frame);
            var spinning = session.run(14, 1000, lookup, frame);
            assertEquals(7, spinning.nodeId(), "spin starts only after reset completed");
            assertEquals(3, runtime.submissions);
            session.run(15, 1000, lookup, frame);
            assertEquals(ProgramVmResult.Status.COMPLETED, session.run(16, 1000, lookup, frame).status());
            assertEquals(3, runtime.submissions);
        }
    }

    @Test
    void unsupportedBlockedAndChangedReceiptsFailExplicitly() {
        var nodeId = id("accelerator", "reset_orientation");
        var configuration = configuration("accelerator", nodeId);
        var executor = executor(definition("accelerator"), nodeId);
        var unsupportedContext = context(new ProgramStructureOrientationRuntime() { }, new HashMap<>());
        unsupportedContext.enterNode(7, nodeId);
        var unavailable = assertThrows(ProgramActionFailure.class,
                () -> executor.execute(unsupportedContext, configuration, inputs(CONTROL)));
        assertEquals(ProgramVmDiagnostic.ACTION_UNAVAILABLE, unavailable.diagnostic());
        var runtime = new ScriptedRuntime();
        var state = new HashMap<String, Object>();
        var context = context(runtime, state);
        context.enterNode(7, nodeId);
        executor.execute(context, configuration, inputs(CONTROL));
        runtime.changedReceipt = true;
        var changed = assertThrows(ProgramActionFailure.class, () -> executor.execute(context, configuration, inputs(CONTROL)));
        assertEquals("rejected:orientation_receipt_changed", changed.detail());
        assertTrue(state.isEmpty());
        runtime.changedReceipt = false;
        executor.execute(context, configuration, inputs(CONTROL));
        runtime.blocked = true;
        var blocked = assertThrows(ProgramActionFailure.class, () -> executor.execute(context, configuration, inputs(CONTROL)));
        assertEquals("rejected:orientation_blocked:wall", blocked.detail());
        assertTrue(state.isEmpty());
    }

    @Test
    void changingTheControlCannotPollThePreviousOwnersReceipt() {
        var runtime = new ScriptedRuntime();
        var state = new HashMap<String, Object>();
        var context = context(runtime, state);
        var nodeId = id("accelerator", "reset_orientation");
        context.enterNode(7, nodeId);
        var executor = executor(definition("accelerator"), nodeId);
        var configuration = configuration("accelerator", nodeId);
        executor.execute(context, configuration, inputs(CONTROL));
        var otherControl = new ProgramStructureControl(CONTROL.reference(), CONTROL.structureRuntimeId(),
                UUID.randomUUID(), CONTROL.actorId(), CONTROL.runtimeToken());
        var failure = assertThrows(ProgramActionFailure.class,
                () -> executor.execute(context, configuration, inputs(otherControl)));
        assertEquals("rejected:orientation_control_changed", failure.detail());
        assertEquals(0, runtime.polls);
        assertEquals(1, runtime.submissions);
        assertTrue(state.isEmpty());
    }

    @Test
    void terminalBackendFailureDiscardsThePendingVmReceipt() {
        for (boolean throwsFailure : List.of(false, true)) {
            var runtime = new ScriptedRuntime();
            var state = new HashMap<String, Object>();
            var context = context(runtime, state);
            var nodeId = id("accelerator", "spin");
            context.enterNode(7, nodeId);
            var executor = executor(definition("accelerator"), nodeId);
            var configuration = configuration("accelerator", nodeId);
            executor.execute(context, configuration, inputs(CONTROL));
            runtime.unavailable = !throwsFailure;
            runtime.throwsFailure = throwsFailure;
            var failure = assertThrows(RuntimeException.class,
                    () -> executor.execute(context, configuration, inputs(CONTROL)));
            if (!throwsFailure) assertEquals(ProgramVmDiagnostic.ACTION_UNAVAILABLE,
                    ((ProgramActionFailure) failure).diagnostic());
            assertTrue(state.isEmpty());
            assertEquals(1, runtime.submissions);
        }
    }

    private static ProgramGraph graph(String category, boolean extended) {
        var host = new JsonObject();
        host.addProperty("dimension", "minecraft:overworld");
        host.addProperty("x", 10); host.addProperty("y", 64); host.addProperty("z", 10);
        var nodes = new ArrayList<>(List.of(
                new ProgramGraph.Node(0, BaseAbilityProgramDefinition.entryId(Identifier.parse("academy:" + category)), 1, new JsonObject()),
                new ProgramGraph.Node(1, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, 1, host),
                new ProgramGraph.Node(2, id(category, "extract"), 1, new JsonObject()),
                new ProgramGraph.Node(3, id(category, "control"), 1, new JsonObject()),
                new ProgramGraph.Node(4, id(category, "rotate"), 1, new JsonObject())));
        var edges = new ArrayList<>(List.of(edge(0, "flow", 2, "flow"), edge(2, "flow", 3, "flow"),
                edge(3, "flow", 4, "flow"), edge(1, "position", 2, "host"),
                edge(2, "structure", 3, "structure"), edge(3, "control", 4, "control")));
        if (extended) {
            var axis = new JsonObject(); axis.addProperty("x", 0); axis.addProperty("y", 1); axis.addProperty("z", 0);
            nodes.add(new ProgramGraph.Node(5, id(category, "reset_orientation"), 1, ProgramStructureOrientationNodes.resetDefaults()));
            nodes.add(new ProgramGraph.Node(6, CommonProgramNodeIds.DIRECTION_CONSTANT, 1, axis));
            nodes.add(new ProgramGraph.Node(7, id(category, "spin"), 1, ProgramStructureOrientationNodes.spinDefaults()));
            edges.addAll(List.of(edge(4, "flow", 5, "flow"), edge(5, "flow", 7, "flow"),
                    edge(3, "control", 5, "control"), edge(3, "control", 7, "control"), edge(6, "direction", 7, "axis")));
        }
        return new ProgramGraph(nodes, edges);
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }

    private static ProgramInputView inputs(ProgramStructureControl control) {
        return new ProgramInputView(Map.of("control", List.of(new ProgramValue<>(ProgramValueTypes.STRUCTURE_CONTROL, control)),
                "axis", List.of(new ProgramValue<>(ProgramValueTypes.DIRECTION, new ProgramDirection(0, 1, 0)))));
    }

    private static ProgramVmContext context(ProgramStructureOrientationRuntime runtime, Map<String, Object> state) {
        return new ProgramVmContext(10, new HashMap<>(), state, new ProgramExecutionFrame(new ProgramActionTransaction(), runtime));
    }

    private static ProgramValue<?> result() {
        return new ProgramValue<>(ProgramValueTypes.STRUCTURE_OPERATION_RESULT, ProgramStructureOperationResult.completed("fixture"));
    }

    private static Identifier id(String category, String action) {
        return Identifier.parse("academy:program/" + category + "/action/structure/" + action);
    }

    private static Identifier capability(String category) {
        return switch (category) {
            case "accelerator" -> AcceleratorProgramCapabilities.STRUCTURE;
            case "electromaster" -> ElectromasterProgramCapabilities.STRUCTURE;
            default -> AeromanipProgramCapabilities.STRUCTURE;
        };
    }

    private static AbilityProgramDefinition definition(String category) {
        return AbilityProgramDefinitions.require(Identifier.parse("academy:" + category));
    }

    private static Object configuration(String category, Identifier nodeId) {
        var definition = definition(category);
        return type(definition, nodeId).configurationCodec().parse(JsonOps.INSTANCE,
                definition.editorCatalog().entry(nodeId).defaultConfiguration()).getOrThrow();
    }

    @SuppressWarnings("unchecked")
    private static ProgramNodeType<Object> type(AbilityProgramDefinition definition, Identifier nodeId) {
        return (ProgramNodeType<Object>) definition.nodeLookup().find(nodeId);
    }

    @SuppressWarnings("unchecked")
    private static ProgramNodeExecutor<Object> executor(AbilityProgramDefinition definition, Identifier nodeId) {
        return (ProgramNodeExecutor<Object>) definition.executors().find(nodeId);
    }

    private static final class ScriptedRuntime implements ProgramStructureOrientationRuntime {
        private UUID operationId;
        private int submissions;
        private int polls;
        private int commandPolls;
        private boolean changedReceipt;
        private boolean blocked;
        private boolean unavailable;
        private boolean throwsFailure;
        private ProgramStructurePose pose;

        @Override public Operation rotateOrientation(ProgramStructureControl control, ProgramStructurePose pose) {
            this.pose = pose;
            return begin(control);
        }
        @Override public Operation resetOrientation(ProgramStructureControl control, ResetRequest request) { return begin(control); }
        @Override public Operation spinStructure(ProgramStructureControl control, SpinRequest request) { return begin(control); }

        private Operation begin(ProgramStructureControl control) {
            assertEquals(CONTROL, control);
            submissions++;
            commandPolls = 0;
            operationId = UUID.randomUUID();
            return Operation.pending(operationId, "controlling");
        }

        @Override public Operation orientationStatus(ProgramStructureControl control, UUID receipt) {
            assertEquals(CONTROL, control);
            assertEquals(operationId, receipt);
            polls++;
            if (throwsFailure) throw new IllegalStateException("backend unavailable");
            if (unavailable) return Operation.unavailable("backend_unavailable");
            if (changedReceipt) return Operation.pending(UUID.randomUUID(), "other_command");
            if (blocked) return Operation.blocked("wall");
            return ++commandPolls == 1 ? Operation.pending(operationId, "controlling") : Operation.completed("done");
        }
    }
}
