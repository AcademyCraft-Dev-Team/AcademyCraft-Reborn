package org.academy.internal.common.ability.program;

import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramAutomaticMetricsTest {
    @Test void nestedDifferentOwnerAndUnavailableClocksPreserveOuterCountersAndBodyException() throws Exception {
        var property = System.getProperty("academy.precisionAutomaticBenchmark");
        System.setProperty("academy.precisionAutomaticBenchmark", "true");
        var outer = UUID.randomUUID(); var inner = UUID.randomUUID();
        var cpuField = ProgramAutomaticMetrics.class.getDeclaredField("cpu");
        var allocationField = ProgramAutomaticMetrics.class.getDeclaredField("allocation");
        cpuField.setAccessible(true); allocationField.setAccessible(true);
        Object originalCpu = null, originalAllocation = null;
        try {
            ProgramAutomaticMetrics.start(Set.of(outer, inner));
            originalCpu = cpuField.get(null); originalAllocation = allocationField.get(null);
            cpuField.set(null, java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{java.lang.management.ThreadMXBean.class}, (_, _, _) -> { throw new SecurityException("unavailable clock"); }));
            allocationField.set(null, java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{com.sun.management.ThreadMXBean.class}, (_, _, _) -> { throw new UnsupportedOperationException("unavailable allocation"); }));
            var expected = new IllegalStateException("business failure");
            try (var ignored = ProgramAutomaticMetrics.dispatch(outer, ProgramTriggers.Type.LOOP)) {
                var actual = assertThrows(IllegalStateException.class, () -> {
                    try (var nested = ProgramAutomaticMetrics.dispatch(inner, ProgramTriggers.Type.HEALTH)) { throw expected; }
                });
                assertSame(expected, actual);
                ProgramAutomaticMetrics.count(ProgramAutomaticMetrics.Counter.ACCEPTED_IMMEDIATE);
                try (var unowned = ProgramAutomaticMetrics.dispatch(UUID.randomUUID(), ProgramTriggers.Type.LOOP)) {
                    ProgramAutomaticMetrics.count(ProgramAutomaticMetrics.Counter.LOCKED);
                }
                ProgramAutomaticMetrics.count(ProgramAutomaticMetrics.Counter.ACCEPTED_IMMEDIATE);
            }
            var snapshot = ProgramAutomaticMetrics.snapshotAndReset();
            var counts = (Map<?, ?>) snapshot.get("counters");
            assertEquals(2L, counts.get("dispatch"));
            assertEquals(2L, counts.get("accepted_immediate"));
            assertEquals(0L, counts.get("locked"));
            var total = (Map<?, ?>) ((Map<?, ?>) snapshot.get("stages")).get("total");
            assertEquals(2L, total.get("calls"));
            assertEquals(-1L, total.get("cpu_ns")); assertEquals(-1L, total.get("allocated_bytes"));
        } finally {
            cpuField.set(null, originalCpu); allocationField.set(null, originalAllocation);
            ProgramAutomaticMetrics.stop();
            if (property == null) System.clearProperty("academy.precisionAutomaticBenchmark");
            else System.setProperty("academy.precisionAutomaticBenchmark", property);
        }
    }

    @Test void observationsIncludeOwnedEarlyExitAndExceptionScopesButExcludeOtherOwners() {
        var previous = System.getProperty("academy.precisionAutomaticBenchmark");
        System.setProperty("academy.precisionAutomaticBenchmark", "true");
        var owner = UUID.randomUUID();
        try {
            ProgramAutomaticMetrics.start(Set.of(owner));
            try (var ignored = ProgramAutomaticMetrics.dispatch(UUID.randomUUID(), ProgramTriggers.Type.LOOP)) {
                ProgramAutomaticMetrics.count(ProgramAutomaticMetrics.Counter.LOCKED);
            }
            assertThrows(IllegalStateException.class, () -> {
                try (var ignored = ProgramAutomaticMetrics.dispatch(owner, ProgramTriggers.Type.HEALTH);
                     var stage = ProgramAutomaticMetrics.stage(ProgramAutomaticMetrics.Stage.BOOK)) {
                    ProgramAutomaticMetrics.count(ProgramAutomaticMetrics.Counter.LOCKED);
                    throw new IllegalStateException("fixture failure");
                }
            });
            var snapshot = ProgramAutomaticMetrics.snapshotAndReset();
            var counters = (Map<?, ?>) snapshot.get("counters");
            assertEquals(1L, counters.get("dispatch"));
            assertEquals(1L, counters.get("health_dispatch"));
            assertEquals(0L, counters.get("loop_dispatch"));
            assertEquals(1L, counters.get("locked"));
            var stages = (Map<?, ?>) snapshot.get("stages");
            assertEquals(1L, ((Map<?, ?>) stages.get("book")).get("calls"));
            assertEquals(1L, ((Map<?, ?>) stages.get("total")).get("calls"));
            assertTrue(((Number) ((Map<?, ?>) stages.get("total")).get("wall_ns")).longValue() >= 0);
            assertEquals(0L, ((Map<?, ?>) ProgramAutomaticMetrics.snapshotAndReset().get("counters")).get("dispatch"));
            assertFalse(AcademyProfiler.isCapturingZones());
        } finally {
            ProgramAutomaticMetrics.stop();
            if (previous == null) System.clearProperty("academy.precisionAutomaticBenchmark");
            else System.setProperty("academy.precisionAutomaticBenchmark", previous);
        }
    }

    @Test void benchmarkUsesValidOrdinaryQueryAndResumableGraphsAndRoundTripsTheirBook() {
        var definition = AbilityProgramDefinitions.require(AcceleratorProgramNodeCatalog.ACCELERATOR);
        for (int wait : new int[]{0, 4, 72000}) {
            var graph = ProgramAutomaticBenchmarkGameTests.program(0, wait);
            var compiled = definition.compile(graph, Set.of());
            assertTrue(compiled.valid(), compiled.diagnostics().toString());
            var book = org.academy.api.common.ability.program.ProgramBook.empty(10).replaceSlot(0, graph);
            var decoded = ProgramBookCodec.decode(ProgramBookCodec.encode(book));
            assertTrue(decoded.valid());
            assertEquals(graph.graph(), decoded.book().slot(0).program().graph());
        }
    }

    @Test void legacyScalarAndArithmeticAliasesRemainASeparateCompatibleControl() {
        var modern = ProgramAutomaticBenchmarkGameTests.program(0, 4);
        var nodes = modern.graph().nodes().stream().map(node -> switch (node.id()) {
            case 3, 13 -> new org.academy.api.common.ability.program.ProgramGraph.Node(node.id(),
                    org.academy.internal.common.ability.program.registry.CommonProgramNodeIds.INTEGER_CONSTANT,
                    1, com.google.gson.JsonParser.parseString("{\"value\":" + (node.id() == 3 ? 1 : 4) + "}"));
            case 5 -> new org.academy.api.common.ability.program.ProgramGraph.Node(node.id(),
                    org.academy.internal.common.ability.program.registry.CommonProgramNodeIds.INTEGER_ADD, 1,
                    com.google.gson.JsonParser.parseString("{}"));
            case 9 -> new org.academy.api.common.ability.program.ProgramGraph.Node(node.id(),
                    org.academy.internal.common.ability.program.registry.CommonProgramNodeIds.FLOAT_CONSTANT, 1,
                    com.google.gson.JsonParser.parseString("{\"value\":16.0}"));
            default -> node;
        }).toList();
        var legacy = new org.academy.api.common.ability.program.AbilityProgram(modern.schemaVersion(), modern.id(), modern.name(), modern.category(),
                new org.academy.api.common.ability.program.ProgramGraph(nodes, modern.graph().edges()), modern.editorLayout());
        var definition = AbilityProgramDefinitions.require(modern.category());
        var migrated = org.academy.internal.common.ability.program.migrate.ProgramGraphMigrations.migrate(legacy, definition.nodeLookup());
        assertFalse(migrated.changed(), "Accelerator EMPTY migrator preserves supported scalar aliases");
        assertEquals(legacy.graph(), migrated.program().graph());
        assertTrue(definition.compile(legacy, Set.of()).valid());
        assertTrue(definition.compile(modern, Set.of()).valid());
    }

    @Test void percentileDefinitionPreservesSampleCountAndUnsupportedCounterState() {
        var result = ProgramAutomaticBenchmarkGameTests.distribution(new long[]{4, 1, 3, 2});
        assertEquals(4, result.get("samples"));
        assertEquals(2L, result.get("p50"));
        assertEquals(4L, result.get("p95"));
        assertEquals(4L, result.get("p99"));
        assertEquals(false, ProgramAutomaticBenchmarkGameTests.distribution(new long[]{-1, -1}).get("available"));
    }
}
