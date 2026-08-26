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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramVmTest {
    private static final Identifier CATEGORY = AcademyCraft.academy("mentalout");
    private static final Identifier ENTRY = AcademyCraft.academy("test/entry");
    private static final Identifier LOOP = AcademyCraft.academy("test/loop");
    private static final Identifier CALL_LOOP = AcademyCraft.academy("test/call_loop");
    private static final Identifier INCREMENT = AcademyCraft.academy("test/increment");
    private static final Identifier YIELD = AcademyCraft.academy("test/yield");
    private static final Identifier STOP = AcademyCraft.academy("test/stop");

    @Test
    void executesLoopAcrossFuelLimitedTimeSlices() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, ENTRY),
                        node(2, LOOP),
                        node(3, INCREMENT)
                ),
                List.of(
                        edge(1, "flow", 2, "flow"),
                        edge(2, "body", 3, "flow"),
                        edge(3, "flow", 2, "flow")
                )
        );
        var session = new ProgramVm.Session(compile(graph));
        var executors = executors();

        ProgramVmResult result = null;
        for (var tick = 0; tick < 10; tick++) {
            result = session.run(tick, 2, executors::get, null);
            if (result.status() == ProgramVmResult.Status.COMPLETED) break;
            assertEquals(ProgramVmResult.Status.FUEL_EXHAUSTED, result.status());
        }

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(3, session.variables().get("counter").value());
    }

    @Test
    void infiniteControlLoopIsPreemptedByFuel() {
        var graph = new ProgramGraph(
                List.of(node(1, ENTRY), node(2, INCREMENT)),
                List.of(
                        edge(1, "flow", 2, "flow"),
                        edge(2, "flow", 2, "flow")
                )
        );
        var session = new ProgramVm.Session(compile(graph));

        var result = session.run(0, 5, executors()::get, null);

        assertEquals(ProgramVmResult.Status.FUEL_EXHAUSTED, result.status());
        assertEquals(4, session.variables().get("counter").value());
        assertEquals(2, session.currentNodeId());
    }

    @Test
    void explicitYieldResumesOnlyAfterWakeTick() {
        var graph = new ProgramGraph(
                List.of(node(1, ENTRY), node(2, YIELD), node(3, STOP)),
                List.of(
                        edge(1, "flow", 2, "flow"),
                        edge(2, "flow", 3, "flow")
                )
        );
        var session = new ProgramVm.Session(compile(graph));
        var executors = executors();

        var yielded = session.run(100, 10, executors::get, null);
        var early = session.run(102, 10, executors::get, null);
        var resumed = session.run(103, 10, executors::get, null);

        assertEquals(ProgramVmResult.Status.SUSPENDED, yielded.status());
        assertEquals(103, session.wakeAt());
        assertEquals(ProgramVmResult.Status.SUSPENDED, early.status());
        assertEquals(ProgramVmResult.Status.COMPLETED, resumed.status());
        assertTrue((Boolean) session.variables().get("stopped").value());
    }

    @Test
    void executesUniqueOpenActionFlowRootWithoutExplicitEntry() {
        var graph = new ProgramGraph(
                List.of(node(2, INCREMENT)),
                List.of()
        );
        var program = compile(graph);
        var session = new ProgramVm.Session(program);

        var result = session.run(0, 5, executors()::get, null);

        assertEquals(2, program.entryNodeId());
        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(1, session.variables().get("counter").value());
    }

    @Test
    void structuredLoopReturnsFromAnUnconnectedBodyAndContinuesAfterAllMembers() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, ENTRY),
                        node(2, CALL_LOOP),
                        node(3, INCREMENT),
                        node(4, STOP)
                ),
                List.of(
                        edge(1, "flow", 2, "flow"),
                        edge(2, "body", 3, "flow"),
                        edge(2, "done", 4, "flow")
                )
        );
        var session = new ProgramVm.Session(compile(graph));

        var result = session.run(0, 20, executors()::get, null);

        assertEquals(ProgramVmResult.Status.COMPLETED, result.status());
        assertEquals(3, session.variables().get("counter").value());
        assertTrue((Boolean) session.variables().get("stopped").value());
    }

    private static CompiledProgram compile(ProgramGraph graph) {
        var types = nodeTypes();
        var result = ProgramCompiler.compile(
                graph,
                new ProgramCompileContext(CATEGORY, Set.of(), ProgramLimits.DEFAULT),
                types::get
        );
        assertTrue(result.valid(), () -> result.diagnostics().toString());
        return result.program();
    }

    private static Map<Identifier, ProgramNodeType<?>> nodeTypes() {
        var result = new HashMap<Identifier, ProgramNodeType<?>>();
        result.put(ENTRY, type(
                ProgramNodeRole.ENTRY,
                new ProgramNodeSchema(
                        List.of(),
                        List.of(flowOutput("flow"))
                )
        ));
        result.put(LOOP, type(
                ProgramNodeRole.CONTROL,
                new ProgramNodeSchema(
                        List.of(new ProgramPortDefinition("flow", ProgramValueTypes.FLOW, true, 2)),
                        List.of(flowOutput("body"), flowOutput("done"))
                )
        ));
        result.put(CALL_LOOP, type(
                ProgramNodeRole.CONTROL,
                new ProgramNodeSchema(
                        List.of(new ProgramPortDefinition("flow", ProgramValueTypes.FLOW, true, 2)),
                        List.of(flowOutput("body"), flowOutput("done"))
                )
        ));
        result.put(INCREMENT, type(
                ProgramNodeRole.ACTION,
                new ProgramNodeSchema(
                        List.of(new ProgramPortDefinition("flow", ProgramValueTypes.FLOW, true, 2)),
                        List.of(flowOutput("flow"))
                )
        ));
        result.put(YIELD, type(
                ProgramNodeRole.SUSPEND,
                new ProgramNodeSchema(
                        List.of(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW)),
                        List.of(flowOutput("flow"))
                )
        ));
        result.put(STOP, type(
                ProgramNodeRole.ACTION,
                new ProgramNodeSchema(
                        List.of(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW)),
                        List.of()
                )
        ));
        return result;
    }

    private static Map<Identifier, ProgramNodeExecutor<?>> executors() {
        var result = new HashMap<Identifier, ProgramNodeExecutor<?>>();
        result.put(LOOP, (context, _, _) -> {
            var counter = context.variable("counter")
                    .map(value -> (Integer) value.value())
                    .orElse(0);
            return ProgramNodeStep.next(counter < 3 ? "body" : "done");
        });
        result.put(CALL_LOOP, (context, _, _) -> {
            var counter = context.variable("counter")
                    .map(value -> (Integer) value.value())
                    .orElse(0);
            return counter < 3
                    ? ProgramNodeStep.call("body", Map.of())
                    : ProgramNodeStep.next("done");
        });
        result.put(INCREMENT, (context, _, _) -> {
            var counter = context.variable("counter")
                    .map(value -> (Integer) value.value())
                    .orElse(0);
            context.setVariable("counter", new ProgramValue<>(ProgramValueTypes.INTEGER, counter + 1));
            return ProgramNodeStep.next("flow");
        });
        result.put(YIELD, (_, _, _) -> ProgramNodeStep.yield("flow", 3));
        result.put(STOP, (context, _, _) -> {
            context.setVariable("stopped", new ProgramValue<>(ProgramValueTypes.BOOLEAN, true));
            return ProgramNodeStep.stop();
        });
        return result;
    }

    private static ProgramNodeType<Unit> type(ProgramNodeRole role, ProgramNodeSchema schema) {
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
                return switch (role) {
                    case SUSPEND -> ProgramNodePurity.SUSPEND;
                    case ACTION -> ProgramNodePurity.ACTION;
                    default -> ProgramNodePurity.PURE;
                };
            }

            @Override
            public ProgramNodeScope scope() {
                return ProgramNodeScope.COMMON;
            }
        };
    }

    private static ProgramPortDefinition flowOutput(String name) {
        return new ProgramPortDefinition(name, ProgramValueTypes.FLOW, false, 1);
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

    private enum Unit {
        INSTANCE
    }
}
