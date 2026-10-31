package org.academy.internal.client.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.internal.common.ability.program.compile.ProgramCompileResult;
import org.academy.internal.common.ability.program.compile.ProgramCompiler;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/** Unshipped, single-thread compile measurements. No graph is ever executed or saved. */
final class PrecisionCompileBenchmark {
    private static volatile ProgramCompileResult blackhole;

    private PrecisionCompileBenchmark() {}

    /** Call before starting any outer profiler capture: attribution is a separate instrumented pass. */
    static JsonObject run(int warmupIterations, int measuredIterations) {
        if (warmupIterations < 0 || warmupIterations > 10_000
                || measuredIterations < 1 || measuredIterations > 10_000) {
            throw new IllegalArgumentException("Expected warmup 0..10000 and measured iterations 1..10000");
        }
        if (AcademyProfiler.isCapturingZones()) {
            throw new IllegalStateException("Compile benchmark requires an independent profiler capture");
        }
        var report = new JsonObject();
        report.addProperty("scope", "single-thread compilation only; valid built-in graphs, no VM/world execution");
        report.addProperty("jvm", System.getProperty("java.vm.name"));
        report.addProperty("java_version", System.getProperty("java.runtime.version"));
        report.addProperty("warmup_iterations_per_case", warmupIterations);
        report.addProperty("measured_iterations_per_case", measuredIterations);
        report.addProperty("percentile_method", "nearest rank, ceil(p*n)-1; warmup excluded");
        report.addProperty("timing_profiler_enabled", false);
        report.addProperty("allocation_scope", "current thread allocated bytes; excludes other threads and native allocations");
        report.addProperty("profiling_scope", "separate capture pass; its counters/times are not mixed with latency samples");
        var cases = new JsonArray();
        try (var allocation = AllocationMeter.open()) {
            report.addProperty("thread_allocation_supported", allocation.available());
            for (var shape : List.of("dense_data_dag", "flow_state_chain")) {
                for (int count : List.of(2, 32, 128)) {
                    var definition = AbilityProgramDefinitions.require(Identifier.parse("academy:accelerator"));
                    var capabilities = PrecisionPerformanceGraphs.capabilities(definition);
                    var program = shape.equals("dense_data_dag")
                            ? PrecisionPerformanceGraphs.editorGraph(count) : flowGraph(count);
                    check(program.graph().nodes().size() == count, "wrong benchmark node count");
                    var context = new ProgramCompileContext(definition.category(), capabilities, definition.limits());
                    for (var path : List.of("definition_with_migration", "direct_compiler")) {
                        Operation operation = path.equals("definition_with_migration")
                                ? () -> definition.compile(program, capabilities)
                                : () -> ProgramCompiler.compile(program.graph(), context, definition.nodeLookup());
                        var result = measure(operation, allocation, warmupIterations, measuredIterations);
                        result.addProperty("shape", shape);
                        result.addProperty("path", path);
                        result.addProperty("nodes", count);
                        result.addProperty("edges", program.graph().edges().size());
                        result.addProperty("all_requests_valid", true);
                        result.addProperty("direct_context_note", "direct compiler context is constructed before measurement");
                        result.add("instrumented_pass", profile(operation, Math.min(128, measuredIterations)));
                        cases.add(result);
                    }
                }
            }
        }
        report.add("cases", cases);
        report.addProperty("passed", true);
        return report;
    }

    private static JsonObject measure(Operation operation, AllocationMeter allocation, int warmup, int measured) {
        for (int i = 0; i < warmup; i++) consume(operation.compile());
        var elapsed = new long[measured];
        var bytes = allocation.available() ? new long[measured] : null;
        long gcCountBefore = gcCount();
        long gcTimeBefore = gcTime();
        for (int i = 0; i < measured; i++) {
            long allocatedBefore = allocation.read();
            long start = System.nanoTime();
            var compiled = operation.compile();
            elapsed[i] = System.nanoTime() - start;
            long allocatedAfter = allocation.read();
            if (bytes != null) {
                check(allocatedBefore >= 0 && allocatedAfter >= allocatedBefore, "thread allocation counter became unavailable");
                bytes[i] = allocatedAfter - allocatedBefore;
            }
            consume(compiled);
        }
        var result = new JsonObject();
        result.add("latency_ns", distribution(elapsed));
        result.add("allocated_bytes_per_request", bytes == null ? JsonNull.INSTANCE : distribution(bytes));
        result.addProperty("timed_request_calls", measured);
        result.addProperty("gc_collections_during_measurement", Math.max(0, gcCount() - gcCountBefore));
        result.addProperty("gc_collection_ms_during_measurement", Math.max(0, gcTime() - gcTimeBefore));
        return result;
    }

    private static JsonObject profile(Operation operation, int iterations) {
        AcademyProfiler.startZoneCapture();
        try {
            for (int i = 0; i < iterations; i++) consume(operation.compile());
        } finally {
            AcademyProfiler.stopZoneCapture();
        }
        var report = new JsonObject();
        report.addProperty("request_calls", iterations);
        var counters = new TreeMap<String, Long>();
        for (var name : List.of("program.compile.actual_compilations", "program.compile.definition_requests",
                "program.compile.migration_requests", "program.compile.node_decode_attempts",
                "program.validation.node_decode_attempts", "program.compile.cache_hit", "program.compile.cache_miss",
                "program.compile.cache_bypass", "program.compile.cache_evict")) counters.put(name, 0L);
        var zones = new JsonArray();
        var snapshot = AcademyProfiler.snapshot().getZones().get(Thread.currentThread().getName());
        check(snapshot != null, "compile profiling produced no thread snapshot");
        for (var slice : snapshot.topSlices(Integer.MAX_VALUE, true)) {
            if (!slice.name().startsWith("academy.program.compile.")) continue;
            var zone = new JsonObject();
            zone.addProperty("name", slice.name());
            zone.addProperty("calls", slice.count());
            zone.addProperty("total_ns", slice.totalNs());
            zone.addProperty("self_ns", slice.selfNs());
            zone.addProperty("max_ns", slice.maxNs());
            zones.add(zone);
            slice.counters().forEach((key, value) -> counters.merge(key, value, Long::sum));
        }
        check(counters.get("program.compile.actual_compilations") > 0
                || counters.get("program.compile.definition_requests") > 0, "compile request counters are missing");
        var encodedCounters = new JsonObject();
        counters.forEach(encodedCounters::addProperty);
        report.add("counters", encodedCounters);
        report.add("zones", zones);
        return report;
    }

    static AbilityProgram flowGraph(int count) {
        if (!List.of(2, 32, 128).contains(count)) throw new IllegalArgumentException("Expected 2, 32 or 128 nodes");
        var definition = AbilityProgramDefinitions.require(Identifier.parse("academy:accelerator"));
        var nodes = new ArrayList<ProgramGraph.Node>();
        var edges = new ArrayList<ProgramGraph.Edge>();
        for (int id = 0; id < count; id++) {
            var type = id == 0 ? BaseAbilityProgramDefinition.entryId(definition.category())
                    : CommonProgramNodeIds.SHARED_VARIABLE_CLEAR_ALL;
            var entry = definition.editorCatalog().entry(type);
            check(entry != null, "benchmark type not registered: " + type);
            nodes.add(new ProgramGraph.Node(id, type, entry.type().schemaVersion(), entry.defaultConfiguration()));
            if (id > 0) edges.add(new ProgramGraph.Edge(new ProgramGraph.Endpoint(id - 1, "flow"),
                    new ProgramGraph.Endpoint(id, "flow")));
        }
        return new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.nameUUIDFromBytes(("compile-flow-" + count).getBytes(StandardCharsets.UTF_8)),
                "Compile flow " + count, definition.category(), new ProgramGraph(nodes, edges), ProgramEditorLayout.EMPTY);
    }

    private static JsonObject distribution(long[] values) {
        var sorted = values.clone();
        Arrays.sort(sorted);
        var result = new JsonObject();
        result.addProperty("samples", sorted.length);
        result.addProperty("min", sorted[0]);
        result.addProperty("p50", percentile(sorted, 0.50));
        result.addProperty("p95", percentile(sorted, 0.95));
        result.addProperty("p99", percentile(sorted, 0.99));
        result.addProperty("max", sorted[sorted.length - 1]);
        result.addProperty("mean", Arrays.stream(values).average().orElseThrow());
        return result;
    }

    private static long percentile(long[] sorted, double fraction) {
        return sorted[Math.max(0, (int) Math.ceil(fraction * sorted.length) - 1)];
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(value -> Math.max(0, value.getCollectionCount())).sum();
    }

    private static long gcTime() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(value -> Math.max(0, value.getCollectionTime())).sum();
    }

    private static void consume(ProgramCompileResult result) {
        check(result.valid(), "benchmark graph rejected: " + result.diagnostics());
        blackhole = result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @FunctionalInterface
    private interface Operation { ProgramCompileResult compile(); }

    private static final class AllocationMeter implements AutoCloseable {
        private final com.sun.management.ThreadMXBean bean;
        private final boolean restoreDisabled;

        private AllocationMeter(com.sun.management.ThreadMXBean bean, boolean restoreDisabled) {
            this.bean = bean;
            this.restoreDisabled = restoreDisabled;
        }

        static AllocationMeter open() {
            var raw = ManagementFactory.getThreadMXBean();
            if (raw instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()) {
                boolean wasEnabled = bean.isThreadAllocatedMemoryEnabled();
                try {
                    if (!wasEnabled) bean.setThreadAllocatedMemoryEnabled(true);
                    return new AllocationMeter(bean, !wasEnabled);
                } catch (UnsupportedOperationException | SecurityException unavailable) { }
            }
            return new AllocationMeter(null, false);
        }

        boolean available() { return bean != null; }
        long read() { return bean == null ? -1 : bean.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
        @Override public void close() { if (restoreDisabled) bean.setThreadAllocatedMemoryEnabled(false); }
    }
}
