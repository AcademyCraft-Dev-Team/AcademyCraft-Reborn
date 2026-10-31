package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.ability.program.compile.CompiledProgram;
import org.academy.internal.common.ability.program.compile.ProgramCompiler;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeCatalog;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.ProgramNodeExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramSamplingVmTest {
    private static final Identifier CATEGORY = AcademyCraft.academy("mentalout");
    private static final Identifier ENTRY = AcademyCraft.academy("test/sample/entry");
    private static final Identifier SOURCE = AcademyCraft.academy("test/sample/source");
    private static final Identifier CAPTURE = AcademyCraft.academy("test/sample/capture");
    private static final Identifier LOOP_CAPTURE = AcademyCraft.academy("test/sample/loop_capture");

    @Test
    void sampleSurvivesMultipleActionsAndWaitWithoutReevaluatingItsSource() {
        var seen = new ArrayList<Object>();
        var queries = new AtomicInteger();
        var fixture = new Fixture(ProgramValueTypes.INTEGER, seen, queries);
        var graph = new ProgramGraph(List.of(node(1, ENTRY), sample(2, ProgramValueTypes.INTEGER),
                node(3, CAPTURE), node(4, CommonProgramNodeIds.WAIT), node(5, CAPTURE),
                node(6, SOURCE), integer(7, 3)), List.of(
                edge(1, "flow", 2, "flow"), edge(2, "flow", 3, "flow"),
                edge(3, "flow", 4, "flow"), edge(4, "flow", 5, "flow"),
                edge(6, "value", 2, "value"), edge(2, "value", 3, "value"),
                edge(2, "value", 5, "value"), edge(7, "value", 4, "ticks")));
        var session = new ProgramVm.Session(fixture.compile(graph));

        assertEquals(ProgramVmResult.Status.SUSPENDED, fixture.run(session, 10).status());
        assertEquals(List.of(1), seen);
        assertEquals(1, queries.get());
        assertEquals(ProgramVmResult.Status.SUSPENDED, fixture.run(session, 12).status());
        assertEquals(ProgramVmResult.Status.COMPLETED, fixture.run(session, 13).status());
        assertEquals(List.of(1, 1), seen);
        assertEquals(1, queries.get());
    }

    @Test
    void revisitingTheSameFlowNodeExplicitlyRefreshesItsSample() {
        var seen = new ArrayList<Object>();
        var queries = new AtomicInteger();
        var fixture = new Fixture(ProgramValueTypes.INTEGER, seen, queries);
        fixture.types.put(LOOP_CAPTURE, type(ProgramNodeRole.CONTROL,
                new ProgramNodeSchema(List.of(flowInput(), input("value", ProgramValueTypes.INTEGER)),
                        List.of(flowOutput("again"), flowOutput("done")))));
        fixture.executors.put(LOOP_CAPTURE, (_, _, inputs) -> {
            seen.add(inputs.require("value", ProgramValueTypes.INTEGER).value());
            return ProgramNodeStep.next(seen.size() < 2 ? "again" : "done");
        });
        var graph = new ProgramGraph(List.of(node(1, ENTRY), sample(2, ProgramValueTypes.INTEGER),
                node(3, LOOP_CAPTURE), node(4, SOURCE)), List.of(
                edge(1, "flow", 2, "flow"), edge(2, "flow", 3, "flow"),
                edge(3, "again", 2, "flow"), edge(4, "value", 2, "value"),
                edge(2, "value", 3, "value")));

        assertEquals(ProgramVmResult.Status.COMPLETED,
                fixture.run(new ProgramVm.Session(fixture.compile(graph)), 0).status());
        assertEquals(List.of(1, 2), seen);
        assertEquals(2, queries.get());
    }

    @Test
    void aNewInvocationDoesNotInheritAnotherInvocationsSample() {
        var seen = new ArrayList<Object>();
        var queries = new AtomicInteger();
        var fixture = new Fixture(ProgramValueTypes.INTEGER, seen, queries);
        var graph = new ProgramGraph(List.of(node(1, ENTRY), sample(2, ProgramValueTypes.INTEGER),
                node(3, CAPTURE), node(4, SOURCE)), List.of(edge(1, "flow", 2, "flow"),
                edge(2, "flow", 3, "flow"), edge(4, "value", 2, "value"),
                edge(2, "value", 3, "value")));
        var compiled = fixture.compile(graph);
        var first = new ProgramVm.Session(compiled);
        var second = new ProgramVm.Session(compiled);
        assertEquals(ProgramVmResult.Status.COMPLETED, fixture.run(first, 0).status());
        assertEquals(ProgramVmResult.Status.COMPLETED, fixture.run(second, 0).status());
        assertEquals(List.of(1, 2), seen);
        assertEquals(2, queries.get());
    }

    @Test
    void readingAnUnexecutedSampleFailsInsteadOfSamplingImplicitly() {
        var seen = new ArrayList<Object>();
        var queries = new AtomicInteger();
        var fixture = new Fixture(ProgramValueTypes.INTEGER, seen, queries);
        var falseConfig = new JsonObject();
        falseConfig.addProperty("value", false);
        var graph = new ProgramGraph(List.of(node(1, ENTRY), sample(2, ProgramValueTypes.INTEGER),
                node(3, CAPTURE), node(4, CommonProgramNodeIds.BRANCH), node(5, SOURCE),
                new ProgramGraph.Node(6, CommonProgramNodeIds.BOOLEAN_CONSTANT, 1, falseConfig)), List.of(
                edge(1, "flow", 4, "flow"), edge(4, "true", 2, "flow"),
                edge(4, "false", 3, "flow"), edge(2, "flow", 3, "flow"),
                edge(5, "value", 2, "value"), edge(2, "value", 3, "value"),
                edge(6, "value", 4, "condition")));

        var result = fixture.run(new ProgramVm.Session(fixture.compile(graph)), 0);
        assertEquals(ProgramVmResult.Status.FAILED, result.status());
        assertEquals(3, result.nodeId());
        assertEquals(ProgramVmDiagnostic.MISSING_INPUT_VALUE, result.diagnostic());
        assertEquals("value", result.port());
        assertEquals(0, queries.get());
        assertTrue(seen.isEmpty());
    }

    @Test
    void graphsWithoutExplicitSamplingKeepPerActionWorldQueries() {
        var seen = new ArrayList<Object>();
        var queries = new AtomicInteger();
        var fixture = new Fixture(ProgramValueTypes.INTEGER, seen, queries);
        var graph = new ProgramGraph(List.of(node(1, ENTRY), node(2, CAPTURE), node(3, CAPTURE),
                node(4, SOURCE)), List.of(edge(1, "flow", 2, "flow"), edge(2, "flow", 3, "flow"),
                edge(4, "value", 2, "value"), edge(4, "value", 3, "value")));

        assertEquals(ProgramVmResult.Status.COMPLETED,
                fixture.run(new ProgramVm.Session(fixture.compile(graph)), 0).status());
        assertEquals(List.of(1, 2), seen);
        assertEquals(2, queries.get());
    }

    @Test
    void aSavedCollectionKeepsMembershipAcrossWaitButNotEntityProperties() {
        var seen = new ArrayList<Object>();
        var queries = new AtomicInteger();
        var fixture = new Fixture(ProgramValueTypes.ENTITY_SET, seen, queries);
        var identity = new AtomicInteger(1);
        var liveMembers = new ArrayList<>(List.of(identity));
        fixture.executors.put(SOURCE, (_, _, _) -> {
            queries.incrementAndGet();
            return ProgramNodeStep.data(Map.of("value", new ProgramValue<>(ProgramValueTypes.ENTITY_SET, liveMembers)));
        });
        var session = new ProgramVm.Session(fixture.compile(waitThenCapture(ProgramValueTypes.ENTITY_SET)));

        assertEquals(ProgramVmResult.Status.SUSPENDED, fixture.run(session, 0).status());
        liveMembers.clear();
        identity.set(9);
        assertEquals(ProgramVmResult.Status.COMPLETED, fixture.run(session, 3).status());
        var members = (List<?>) seen.getFirst();
        assertEquals(1, members.size());
        assertSame(identity, members.getFirst());
        assertEquals(9, ((AtomicInteger) members.getFirst()).get());
        assertEquals(1, queries.get());
    }

    @Test
    void samplingDoesNotOverrideAnUpstreamRandomNumbersExistingLatch() {
        var seen = new ArrayList<Object>();
        var fixture = new Fixture(ProgramValueTypes.FLOAT, seen, new AtomicInteger());
        fixture.types.put(LOOP_CAPTURE, type(ProgramNodeRole.CONTROL,
                new ProgramNodeSchema(List.of(flowInput(), input("value", ProgramValueTypes.FLOAT)),
                        List.of(flowOutput("again"), flowOutput("done")))));
        fixture.executors.put(LOOP_CAPTURE, (_, _, inputs) -> {
            seen.add(inputs.require("value", ProgramValueTypes.FLOAT).value());
            return ProgramNodeStep.next(seen.size() < 2 ? "again" : "done");
        });
        var random = new JsonObject();
        random.addProperty("type", "float");
        random.addProperty("lower", "0");
        random.addProperty("upper", "1");
        var graph = new ProgramGraph(List.of(node(1, ENTRY), sample(2, ProgramValueTypes.FLOAT),
                node(3, LOOP_CAPTURE), new ProgramGraph.Node(4, CommonProgramNodeIds.RANDOM_NUMBER, 1, random)),
                List.of(edge(1, "flow", 2, "flow"), edge(2, "flow", 3, "flow"),
                        edge(3, "again", 2, "flow"), edge(4, "value", 2, "value"),
                        edge(2, "value", 3, "value")));
        assertEquals(ProgramVmResult.Status.COMPLETED,
                fixture.run(new ProgramVm.Session(fixture.compile(graph)), 0).status());
        assertEquals(2, seen.size());
        assertEquals(seen.get(0), seen.get(1));
    }

    @Test
    void downstreamValidityQueryStillObservesTheLiveStructureAfterWait() {
        var seen = new ArrayList<Object>();
        var queries = new AtomicInteger();
        var fixture = new Fixture(ProgramValueTypes.BOOLEAN, seen, queries);
        var reference = new ProgramStructureReference(new StructureRef(UUID.randomUUID(),
                Identifier.parse("minecraft:overworld"), AcademyCraft.academy("native"), 0), UUID.randomUUID());
        fixture.types.put(SOURCE, type(ProgramNodeRole.QUERY, new ProgramNodeSchema(List.of(),
                List.of(ProgramPortDefinition.output("value", ProgramValueTypes.STRUCTURE_REFERENCE)))));
        fixture.executors.put(SOURCE, (_, _, _) -> ProgramNodeStep.data(Map.of("value",
                new ProgramValue<>(ProgramValueTypes.STRUCTURE_REFERENCE, reference))));
        var valid = new AtomicInteger(1);
        var validations = new AtomicInteger();
        ProgramTargetResolver resolver = new ProgramTargetResolver() {
            @Override public boolean isStructureValid(ProgramStructureReference candidate) {
                assertSame(reference, candidate);
                validations.incrementAndGet();
                return valid.get() == 1;
            }
            @Override public Optional<ProgramWorldPosition> positionOf(Object reference) { return Optional.empty(); }
            @Override public Optional<ProgramDirection> lookDirectionOf(Object reference) { return Optional.empty(); }
            @Override public List<?> entitiesAround(ProgramWorldPosition center, double radius) { return List.of(); }
            @Override public Optional<ProgramBlockPosition> raycastBlock(ProgramWorldPosition origin,
                    ProgramDirection direction, double maximumDistance) { return Optional.empty(); }
        };
        var graph = new ProgramGraph(List.of(node(1, ENTRY), sample(2, ProgramValueTypes.STRUCTURE_REFERENCE),
                node(3, CAPTURE), node(4, CommonProgramNodeIds.WAIT), node(5, CAPTURE), node(6, SOURCE),
                integer(7, 3), node(8, CommonProgramNodeIds.STRUCTURE_VALID)), List.of(
                edge(1, "flow", 2, "flow"), edge(2, "flow", 3, "flow"),
                edge(3, "flow", 4, "flow"), edge(4, "flow", 5, "flow"),
                edge(6, "value", 2, "value"), edge(2, "value", 8, "structure"),
                edge(8, "result", 3, "value"), edge(8, "result", 5, "value"),
                edge(7, "value", 4, "ticks")));
        var session = new ProgramVm.Session(fixture.compile(graph));
        var frame = new ProgramExecutionFrame(ProgramActionTransaction.sequential(), resolver);
        assertEquals(ProgramVmResult.Status.SUSPENDED, session.run(0, 100, fixture::executor, frame).status());
        valid.set(0);
        assertEquals(ProgramVmResult.Status.COMPLETED, session.run(3, 100, fixture::executor, frame).status());
        assertEquals(List.of(true, false), seen);
        assertEquals(2, validations.get());
    }

    private static ProgramGraph waitThenCapture(ProgramValueType type) {
        return new ProgramGraph(List.of(node(1, ENTRY), sample(2, type), node(3, CAPTURE),
                node(4, CommonProgramNodeIds.WAIT), node(5, SOURCE), integer(6, 3)), List.of(
                edge(1, "flow", 2, "flow"), edge(2, "flow", 4, "flow"),
                edge(4, "flow", 3, "flow"), edge(5, "value", 2, "value"),
                edge(2, "value", 3, "value"), edge(6, "value", 4, "ticks")));
    }

    private static ProgramGraph.Node node(int id, Identifier type) {
        return new ProgramGraph.Node(id, type, 1, new JsonObject());
    }

    private static ProgramGraph.Node sample(int id, ProgramValueType type) {
        var configuration = new JsonObject();
        configuration.addProperty("type", type.id().toString());
        return new ProgramGraph.Node(id, CommonProgramNodeIds.SAMPLE_VALUE, 1, configuration);
    }

    private static ProgramGraph.Node integer(int id, int value) {
        var configuration = new JsonObject();
        configuration.addProperty("value", value);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.INTEGER_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }

    private static ProgramPortDefinition flowInput() {
        return new ProgramPortDefinition("flow", ProgramValueTypes.FLOW, true, Integer.MAX_VALUE);
    }

    private static ProgramPortDefinition flowOutput(String port) {
        return new ProgramPortDefinition(port, ProgramValueTypes.FLOW, false, 1);
    }

    private static ProgramPortDefinition input(String port, ProgramValueType type) {
        return ProgramPortDefinition.requiredInput(port, type);
    }

    private static ProgramNodeType<Boolean> type(ProgramNodeRole role, ProgramNodeSchema schema) {
        return new ProgramNodeType<>() {
            @Override public Codec<Boolean> configurationCodec() { return MapCodec.unit(false).codec(); }
            @Override public int schemaVersion() { return 1; }
            @Override public ProgramNodeRole role() { return role; }
            @Override public ProgramNodePurity purity() {
                return role == ProgramNodeRole.QUERY ? ProgramNodePurity.WORLD_QUERY : ProgramNodePurity.STATE;
            }
            @Override public ProgramNodeScope scope() { return ProgramNodeScope.COMMON; }
            @Override public ProgramNodeSchema schema(Boolean configuration) { return schema; }
        };
    }

    private static final class Fixture {
        private final Map<Identifier, ProgramNodeType<?>> types = new HashMap<>();
        private final Map<Identifier, ProgramNodeExecutor<?>> executors = new HashMap<>();

        private Fixture(ProgramValueType type, List<Object> seen, AtomicInteger queries) {
            types.put(ENTRY, type(ProgramNodeRole.ENTRY, new ProgramNodeSchema(List.of(), List.of(flowOutput("flow")))));
            types.put(SOURCE, type(ProgramNodeRole.QUERY, new ProgramNodeSchema(List.of(),
                    List.of(ProgramPortDefinition.output("value", type)))));
            types.put(CAPTURE, type(ProgramNodeRole.CONTROL, new ProgramNodeSchema(
                    List.of(flowInput(), input("value", type)), List.of(flowOutput("flow")))));
            executors.put(SOURCE, (_, _, _) -> ProgramNodeStep.data(Map.of("value",
                    new ProgramValue<>(type, queries.incrementAndGet()))));
            executors.put(CAPTURE, (_, _, inputs) -> {
                seen.add(inputs.require("value", type).value());
                return ProgramNodeStep.next("flow");
            });
        }

        private CompiledProgram compile(ProgramGraph graph) {
            var compiled = ProgramCompiler.compile(graph,
                    new ProgramCompileContext(CATEGORY, Set.of(), ProgramLimits.DEFAULT), id -> {
                        var testType = types.get(id);
                        return testType == null ? CommonProgramNodeCatalog.INSTANCE.find(id) : testType;
                    });
            assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
            return compiled.program();
        }

        private ProgramNodeExecutor<?> executor(Identifier id) {
            var testExecutor = executors.get(id);
            return testExecutor == null ? CommonProgramExecutors.INSTANCE.find(id) : testExecutor;
        }

        private ProgramVmResult run(ProgramVm.Session session, long time) {
            return session.run(time, 100, this::executor, null);
        }
    }
}
