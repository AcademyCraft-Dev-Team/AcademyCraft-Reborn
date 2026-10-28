package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramStructureGroupNodesTest {
    private static final List<String> CATEGORIES = List.of("accelerator", "electromaster", "aeromanip");
    private static final ProgramStructureReference TARGET = new ProgramStructureReference(new StructureRef(UUID.randomUUID(),
            Identifier.parse("minecraft:overworld"), Identifier.parse("academy:test"), 1), UUID.randomUUID());

    @Test void eachRegisteredActionSnapshotsOnceAndPollsWithoutReacquiringOrRetargeting() {
        for (var category : CATEGORIES) for (var action : List.of("rotate_group", "reset_group", "spin_group", "move_group")) {
            var runtime = new Runtime();
            var definition = AbilityProgramDefinitions.require(Identifier.parse("academy:" + category));
            var id = action(category, action);
            var type = definition.nodeLookup().find(id);
            var config = type.configurationCodec().parse(JsonOps.INSTANCE, definition.editorCatalog().entry(id).defaultConfiguration()).getOrThrow();
            @SuppressWarnings("unchecked") var executor = (ProgramNodeExecutor<Object>) definition.executors().find(id);
            var state = new HashMap<String, Object>();
            var context = new ProgramVmContext(0, new HashMap<>(), state, new ProgramExecutionFrame(new ProgramActionTransaction(), runtime));
            context.enterNode(7, id);
            assertEquals(ProgramNodeStep.Directive.RETRY, executor.execute(context, config, inputs(List.of(TARGET, TARGET))).directive());
            assertEquals(List.of(TARGET), runtime.targets);
            assertEquals(ProgramNodeStep.Directive.RETRY, executor.execute(context, config, inputs(List.of())).directive());
            assertEquals(ProgramNodeStep.Directive.CONTINUE, executor.execute(context, config, inputs(List.of())).directive());
            assertEquals(1, runtime.starts); assertEquals(2, runtime.polls); assertTrue(state.isEmpty());
            assertTrue(definition.editorCatalog().entry(id).visible());
            assertEquals(ProgramValueTypes.STRUCTURE_SET, definition.editorCatalog().schema(id,
                    definition.editorCatalog().entry(id).defaultConfiguration()).input("structures").orElseThrow().type());
        }
    }

    @Test void changedReceiptsFailAndSchedulerCleanupReleasesGroupsEvenIfSingleCleanupThrows() {
        var runtime = new Runtime(); runtime.changeReceipt = true;
        var state = new HashMap<String, Object>();
        var frame = new ProgramExecutionFrame(new ProgramActionTransaction(), runtime);
        var context = new ProgramVmContext(0, new HashMap<>(), state, frame);
        context.enterNode(9, action("accelerator", "rotate_group"));
        ProgramStructureGroupExecutors.rotate(context, false, inputs(List.of(TARGET)));
        assertThrows(ProgramActionFailure.class, () -> ProgramStructureGroupExecutors.rotate(context, false, inputs(List.of(TARGET))));
        assertTrue(state.isEmpty());
        ServerProgramExecution.closeOrientationCommands(frame);
        assertEquals(1, runtime.closes);
    }

    @Test void allThreeCategoryGraphsSaveRoundTripAndRunTheRealVmWithBoundedGroupInputs() {
        for (var category : CATEGORIES) {
            var definition = AbilityProgramDefinitions.require(Identifier.parse("academy:" + category));
            var setId = CommonProgramNodeCatalog.CollectionDomain.STRUCTURE.id("empty");
            var set = new JsonObject(); set.addProperty("inputs", 0);
            var graph = new ProgramGraph(List.of(
                    new ProgramGraph.Node(0, BaseAbilityProgramDefinition.entryId(definition.category()), 1, new JsonObject()),
                    new ProgramGraph.Node(1, setId, 1, set),
                    new ProgramGraph.Node(2, action(category, "reset_group"), 1, ProgramStructureOrientationNodes.resetDefaults())),
                    List.of(edge(0, "flow", 2, "flow"), edge(1, "values", 2, "structures")));
            var program = new AbilityProgram(1, UUID.randomUUID(), "groups", definition.category(), graph, ProgramEditorLayout.EMPTY);
            var decoded = ProgramBookCodec.decodeProgram(ProgramBookCodec.encodeProgram(program));
            assertTrue(decoded.valid()); assertEquals(program, decoded.program());
            var skill = Identifier.parse("academy:" + switch (category) {
                case "accelerator" -> "kinetic_throw"; case "electromaster" -> "magnet_manipulation"; default -> "high_speed_jet";
            });
            assertFalse(definition.compile(graph, Set.of()).valid(), "group actions must retain the skill capability gate");
            var compiled = definition.compile(graph, Set.of(skill));
            assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
            var runtime = new Runtime(); runtime.emptyCompletes = true;
            var session = new ProgramVm.Session(compiled.program());
            var result = session.run(0, 1000, definition.executors(), new ProgramExecutionFrame(new ProgramActionTransaction(), runtime));
            assertEquals(ProgramVmResult.Status.COMPLETED, result.status(), result::toString);
            assertEquals(List.of(), runtime.targets); assertEquals(0, runtime.polls);
        }
    }

    private static ProgramInputView inputs(List<ProgramStructureReference> targets) {
        return new ProgramInputView(Map.of("structures", List.of(new ProgramValue<>(ProgramValueTypes.STRUCTURE_SET, targets)),
                "displacement", List.of(new ProgramValue<>(ProgramValueTypes.VECTOR, new ProgramVector(1, 2, 3))),
                "axis", List.of(new ProgramValue<>(ProgramValueTypes.DIRECTION, new ProgramDirection(0, 1, 0))),
                "pose", List.of(new ProgramValue<>(ProgramValueTypes.STRUCTURE_POSE, new ProgramStructurePose(new ProgramDirection(0, 1, 0), 0.5)))));
    }
    private static Identifier action(String category, String action) { return Identifier.parse("academy:program/" + category + "/action/structure/" + action); }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
    private static final class Runtime implements ProgramStructureGroupRuntime, ProgramStructureOrientationRuntime {
        int starts, polls, closes;
        boolean changeReceipt, emptyCompletes;
        UUID receipt;
        List<ProgramStructureReference> targets;
        private Operation start(List<ProgramStructureReference> targets) {
            starts++; this.targets = targets; receipt = UUID.randomUUID();
            return targets.isEmpty() && emptyCompletes ? Operation.completed("empty") : Operation.pending(receipt, "pending");
        }
        @Override public Operation rotateStructures(List<ProgramStructureReference> targets, ProgramStructurePose pose) { return start(targets); }
        @Override public Operation moveStructures(List<ProgramStructureReference> targets, ProgramStructureMoveRequest request) {
            assertEquals(new ProgramVector(1, 2, 3), request.displacement()); return start(targets);
        }
        @Override public Operation resetStructures(List<ProgramStructureReference> targets, ResetRequest request) { return start(targets); }
        @Override public Operation spinStructures(List<ProgramStructureReference> targets, SpinRequest request) { return start(targets); }
        @Override public Operation structureGroupStatus(UUID receipt) {
            assertEquals(this.receipt, receipt); polls++;
            return polls < 2 ? Operation.pending(changeReceipt ? UUID.randomUUID() : receipt, "pending") : Operation.completed("done");
        }
        @Override public void closeOrientationCommands() { throw new IllegalStateException("injected single command cleanup failure"); }
        @Override public void closeStructureGroups() { closes++; }
    }
}
