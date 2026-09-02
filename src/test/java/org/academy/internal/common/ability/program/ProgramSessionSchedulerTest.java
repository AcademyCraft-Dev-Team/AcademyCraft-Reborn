package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramSessionSchedulerTest {
    private static final Identifier CATEGORY = AcademyCraft.academy("mentalout");
    private static final Identifier ENTRY = AcademyCraft.academy("test/scheduler_entry");
    private static final Identifier YIELD = AcademyCraft.academy("test/scheduler_yield");
    private static final Identifier LOOP = AcademyCraft.academy("test/scheduler_loop");
    private static final Identifier STOP = AcademyCraft.academy("test/scheduler_stop");

    @Test
    void resumesYieldedProgramAndReportsCompletion() {
        var scheduler = new ProgramSessionScheduler<String>();
        var terminations = new ArrayList<ProgramSessionScheduler.Termination>();
        assertTrue(scheduler.start(
                "session",
                compile(List.of(YIELD, STOP)),
                executors()::get,
                null,
                8,
                0,
                10,
                (_, termination) -> terminations.add(termination)
        ));

        scheduler.tick(0);
        scheduler.tick(2);
        assertEquals(1, scheduler.size());
        scheduler.tick(3);

        assertEquals(0, scheduler.size());
        assertEquals(1, terminations.size());
        assertEquals(
                ProgramSessionScheduler.TerminationKind.COMPLETED,
                terminations.getFirst().kind()
        );
    }

    @Test
    void expiresFuelBoundInfiniteProgramAndRejectsDuplicateKey() {
        var scheduler = new ProgramSessionScheduler<String>();
        var terminations = new ArrayList<ProgramSessionScheduler.Termination>();
        var program = compile(List.of(LOOP));
        assertTrue(scheduler.start(
                "session",
                program,
                executors()::get,
                null,
                2,
                10,
                2,
                (_, termination) -> terminations.add(termination)
        ));
        assertFalse(scheduler.start(
                "session",
                program,
                executors()::get,
                null,
                2,
                10,
                2,
                (_, _) -> {
                }
        ));

        scheduler.tick(10);
        scheduler.tick(11);
        scheduler.tick(12);

        assertEquals(0, scheduler.size());
        assertEquals(
                ProgramSessionScheduler.TerminationKind.EXPIRED,
                terminations.getFirst().kind()
        );
    }

    @Test
    void cancelledSessionFromAnotherCallbackDoesNotRunFromTickSnapshot() {
        var scheduler = new ProgramSessionScheduler<String>();
        var executions = new AtomicInteger();
        var program = compile(List.of(STOP));
        ProgramExecutorLookup executors = type -> type.equals(STOP)
                ? (_, _, _) -> {
            executions.incrementAndGet();
            return ProgramNodeStep.stop();
        }
                : null;
        assertTrue(scheduler.start(
                "first", program, executors, null, 4, 0, 10,
                (_, _) -> scheduler.cancel("second")
        ));
        assertTrue(scheduler.start(
                "second", program, executors, null, 4, 0, 10, (_, _) -> {
                }
        ));

        scheduler.tick(0);

        assertEquals(1, executions.get());
        assertEquals(0, scheduler.size());
    }

    @Test
    void targetedTickAdvancesOnlyTheSelectedSession() {
        var scheduler = new ProgramSessionScheduler<String>();
        var program = compile(List.of(STOP));
        assertTrue(scheduler.start(
                "first", program, executors()::get, null, 4, 0, 10, (_, _) -> {
                }
        ));
        assertTrue(scheduler.start(
                "second", program, executors()::get, null, 4, 0, 10, (_, _) -> {
                }
        ));

        scheduler.tick("first", 0);

        assertFalse(scheduler.contains("first"));
        assertTrue(scheduler.contains("second"));
        scheduler.tick("second", 0);
        assertEquals(0, scheduler.size());
    }

    private static CompiledProgram compile(List<Identifier> flowNodes) {
        var nodes = new ArrayList<ProgramGraph.Node>();
        var edges = new ArrayList<ProgramGraph.Edge>();
        nodes.add(node(0, ENTRY));
        for (var index = 0; index < flowNodes.size(); index++) {
            var id = index + 1;
            nodes.add(node(id, flowNodes.get(index)));
            edges.add(edge(id - 1, "flow", id, "flow"));
        }
        if (flowNodes.size() == 1 && flowNodes.getFirst().equals(LOOP)) {
            edges.add(edge(1, "flow", 1, "flow"));
        }
        var types = Map.<Identifier, ProgramNodeType<?>>of(
                ENTRY, type(ProgramNodeRole.ENTRY, false),
                YIELD, type(ProgramNodeRole.SUSPEND, true),
                LOOP, type(ProgramNodeRole.ACTION, true),
                STOP, type(ProgramNodeRole.ACTION, false)
        );
        var result = ProgramCompiler.compile(
                new ProgramGraph(nodes, edges),
                new ProgramCompileContext(CATEGORY, Set.of(), ProgramLimits.DEFAULT),
                types::get
        );
        assertTrue(result.valid(), () -> result.diagnostics().toString());
        return result.program();
    }

    private static Map<Identifier, ProgramNodeExecutor<?>> executors() {
        return Map.of(
                YIELD, (_, _, _) -> ProgramNodeStep.yield("flow", 3),
                LOOP, (_, _, _) -> ProgramNodeStep.next("flow"),
                STOP, (_, _, _) -> ProgramNodeStep.stop()
        );
    }

    private static ProgramNodeType<Unit> type(ProgramNodeRole role, boolean hasFlowOutput) {
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
                return new ProgramNodeSchema(
                        role == ProgramNodeRole.ENTRY
                                ? List.of()
                                : List.of(role == ProgramNodeRole.ACTION && hasFlowOutput
                                ? new ProgramPortDefinition(
                                "flow", ProgramValueTypes.FLOW, true, 2)
                                : ProgramPortDefinition.requiredInput(
                                "flow", ProgramValueTypes.FLOW)),
                        hasFlowOutput || role == ProgramNodeRole.ENTRY
                                ? List.of(ProgramPortDefinition.output(
                                "flow", ProgramValueTypes.FLOW))
                                : List.of()
                );
            }

            @Override
            public ProgramNodeRole role() {
                return role;
            }

            @Override
            public ProgramNodePurity purity() {
                return role == ProgramNodeRole.SUSPEND
                        ? ProgramNodePurity.SUSPEND
                        : ProgramNodePurity.ACTION;
            }

            @Override
            public ProgramNodeScope scope() {
                return ProgramNodeScope.COMMON;
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

    private enum Unit {
        INSTANCE
    }
}
