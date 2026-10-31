package org.academy.internal.client.validation;

import org.academy.api.common.profiler.AcademyProfiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrecisionCompileBenchmarkTest {
    @Test
    void measurementsExcludeWarmupAndCountRealSuccessfulCompilationWork() {
        var report = PrecisionCompileBenchmark.run(2, 4);
        assertTrue(report.get("passed").getAsBoolean());
        assertFalse(report.get("timing_profiler_enabled").getAsBoolean());
        assertEquals(12, report.getAsJsonArray("cases").size());
        for (var value : report.getAsJsonArray("cases")) {
            var row = value.getAsJsonObject();
            int nodes = row.get("nodes").getAsInt();
            assertEquals(4, row.getAsJsonObject("latency_ns").get("samples").getAsInt());
            var profiling = row.getAsJsonObject("instrumented_pass");
            var counters = profiling.getAsJsonObject("counters");
            assertEquals(4, profiling.get("request_calls").getAsInt());
            if (row.get("path").getAsString().equals("definition_with_migration")) {
                assertEquals(4, counters.get("program.compile.definition_requests").getAsInt());
                assertEquals(4, counters.get("program.compile.migration_requests").getAsInt());
                assertEquals(4, counters.get("program.compile.cache_hit").getAsInt());
                assertEquals(0, counters.get("program.compile.actual_compilations").getAsInt());
            } else {
                assertEquals(0, counters.get("program.compile.definition_requests").getAsInt());
                assertEquals(4, counters.get("program.compile.actual_compilations").getAsInt());
                assertEquals(4 * nodes, counters.get("program.compile.node_decode_attempts").getAsInt());
                assertEquals(4 * nodes, counters.get("program.validation.node_decode_attempts").getAsInt());
            }
        }
        assertFalse(AcademyProfiler.isCapturingZones(), "benchmark must leave capture disabled");
    }

    @Test
    void invalidIterationBoundsDoNotStartProfiling() {
        assertThrows(IllegalArgumentException.class, () -> PrecisionCompileBenchmark.run(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> PrecisionCompileBenchmark.run(0, 0));
        assertThrows(IllegalArgumentException.class, () -> PrecisionCompileBenchmark.run(0, 10_001));
        assertFalse(AcademyProfiler.isCapturingZones());
    }
}
