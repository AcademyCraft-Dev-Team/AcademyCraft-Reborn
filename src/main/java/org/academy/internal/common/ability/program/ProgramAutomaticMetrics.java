package org.academy.internal.common.ability.program;

import org.academy.api.common.profiler.AcademyProfiler;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fixed-cardinality observations; recording is opt-in and owned by the server-thread fixture. */
final class ProgramAutomaticMetrics {
    enum Stage {
        TOTAL("total"), BOOK("book"), BASE64("base64"), BOOK_CODEC("book_codec"),
        BOOK_MIGRATE("book_migrate"), CAPABILITIES("capabilities"), MATCH("match"),
        SESSION_LOOKUP("session_lookup"), COMPILE("compile"), EXECUTE("execute"),
        SCHEDULER_TICK("scheduler_tick");
        final String label;
        Stage(String label) { this.label = "academy.program.trigger." + label; }
    }

    enum Counter {
        DISPATCH, LOOP_DISPATCH, HEALTH_DISPATCH, LOCKED, CATEGORY_UNAVAILABLE, DATA_UNAVAILABLE,
        SLOT_EXAMINED, MATCHED, ALREADY_RUNNING, COMPILE_INVALID, FAILED_RETRY_WAIT,
        BUDGET_REJECTED, ADMISSION_ATTEMPT, ACCEPTED_IMMEDIATE, ACCEPTED_DEFERRED, EXECUTION_REJECTED,
        SESSION_ADMITTED, SESSION_REJECTED, SESSION_TICK, SESSION_COMPLETED, SESSION_FAILED,
        SESSION_EXPIRED, SESSION_CANCELLED, BOOK_DECODE_HIT, BOOK_DECODE_MISS, BOOK_DECODE_BYPASS, BOOK_DECODE_EVICT;
        final String label = "program.trigger." + name().toLowerCase(java.util.Locale.ROOT);
    }

    private static final Scope DISABLED = new Scope(null, false, false, false, false);
    private static final long[][] totals = new long[Stage.values().length][4];
    private static final long[] counts = new long[Counter.values().length];
    private static Set<UUID> owners = Set.of();
    private static boolean dispatchRecorded;
    private static java.lang.management.ThreadMXBean cpu;
    private static com.sun.management.ThreadMXBean allocation;
    private static boolean restoreCpuDisabled, restoreAllocationDisabled;

    private ProgramAutomaticMetrics() {}

    static void start(Set<UUID> playerIds) {
        if (!Boolean.getBoolean("academy.precisionAutomaticBenchmark")) {
            throw new IllegalStateException("Automatic metrics require the isolated opt-in benchmark");
        }
        if (!owners.isEmpty()) throw new IllegalStateException("Automatic metrics already owned");
        try {
            cpu = ManagementFactory.getThreadMXBean();
            if (!cpu.isCurrentThreadCpuTimeSupported()) cpu = null;
            else {
                restoreCpuDisabled = !cpu.isThreadCpuTimeEnabled();
                if (restoreCpuDisabled) cpu.setThreadCpuTimeEnabled(true);
            }
        } catch (RuntimeException ignored) { cpu = null; }
        if (cpu instanceof com.sun.management.ThreadMXBean bean) {
            try {
                if (bean.isThreadAllocatedMemorySupported()) {
                    restoreAllocationDisabled = !bean.isThreadAllocatedMemoryEnabled();
                    if (restoreAllocationDisabled) bean.setThreadAllocatedMemoryEnabled(true);
                    allocation = bean;
                }
            } catch (RuntimeException ignored) { allocation = null; }
        }
        owners = Set.copyOf(playerIds);
        reset();
    }

    static void stop() {
        owners = Set.of(); dispatchRecorded = false;
        try { if (cpu != null && restoreCpuDisabled) cpu.setThreadCpuTimeEnabled(false); }
        catch (RuntimeException ignored) { }
        try { if (allocation != null && restoreAllocationDisabled) allocation.setThreadAllocatedMemoryEnabled(false); }
        catch (RuntimeException ignored) { }
        cpu = null; allocation = null; restoreCpuDisabled = false; restoreAllocationDisabled = false;
        reset();
    }

    static boolean cpuAvailable() { return cpu != null; }
    static boolean allocationAvailable() { return allocation != null; }
    static long cpuNow() {
        try { return cpu == null ? -1 : cpu.getCurrentThreadCpuTime(); }
        catch (RuntimeException ignored) { return -1; }
    }
    static long allocatedNow() {
        try { return allocation == null ? -1 : allocation.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
        catch (RuntimeException ignored) { return -1; }
    }

    static Scope scheduler(UUID owner) {
        return context(Stage.SCHEDULER_TICK, owner);
    }

    static Scope dispatch(UUID owner, ProgramTriggers.Type trigger) {
        var scope = context(Stage.TOTAL, owner);
        count(Counter.DISPATCH);
        if (trigger == ProgramTriggers.Type.LOOP) count(Counter.LOOP_DISPATCH);
        if (trigger == ProgramTriggers.Type.HEALTH) count(Counter.HEALTH_DISPATCH);
        return scope;
    }

    static Scope stage(Stage stage) {
        boolean profile = capturing();
        if (!dispatchRecorded && !profile) return DISABLED;
        return new Scope(stage, dispatchRecorded, push(stage, profile), false, false);
    }

    private static Scope context(Stage stage, UUID owner) {
        boolean previous = dispatchRecorded;
        dispatchRecorded = owners.contains(owner);
        boolean profile = capturing();
        if (!previous && !dispatchRecorded && !profile) return DISABLED;
        return new Scope(stage, dispatchRecorded, push(stage, profile), true, previous);
    }

    private static boolean capturing() {
        try { return AcademyProfiler.isCapturingZones(); }
        catch (RuntimeException ignored) { return false; }
    }

    private static boolean push(Stage stage, boolean profile) {
        if (!profile) return false;
        try { AcademyProfiler.push(stage.label); return true; }
        catch (RuntimeException ignored) { return false; }
    }

    static void count(Counter counter) {
        if (dispatchRecorded) counts[counter.ordinal()]++;
        try { AcademyProfiler.incrementCounter(counter.label); }
        catch (RuntimeException ignored) { }
    }

    static Map<String, Object> snapshotAndReset() {
        var result = new LinkedHashMap<String, Object>();
        var stages = new LinkedHashMap<String, Object>();
        for (var stage : Stage.values()) {
            var row = totals[stage.ordinal()];
            stages.put(stage.name().toLowerCase(java.util.Locale.ROOT), Map.of(
                    "calls", row[0], "wall_ns", row[1], "cpu_ns", cpu == null ? -1L : row[2],
                    "allocated_bytes", allocation == null ? -1L : row[3]));
        }
        var counters = new LinkedHashMap<String, Long>();
        for (var counter : Counter.values()) counters.put(counter.name().toLowerCase(java.util.Locale.ROOT), counts[counter.ordinal()]);
        result.put("stages", stages); result.put("counters", counters);
        reset();
        return result;
    }

    private static void reset() {
        for (var row : totals) Arrays.fill(row, 0L);
        Arrays.fill(counts, 0L);
    }

    static final class Scope implements AutoCloseable {
        private final Stage stage;
        private final boolean recording, profiling, restoreContext, previousContext;
        private final long wall, cpuStart, allocationStart;
        private boolean closed;

        private Scope(Stage stage, boolean recording, boolean profiling, boolean restoreContext, boolean previousContext) {
            this.stage = stage; this.recording = recording; this.profiling = profiling;
            this.restoreContext = restoreContext; this.previousContext = previousContext;
            cpuStart = recording ? cpuNow() : -1;
            allocationStart = recording ? allocatedNow() : -1;
            wall = recording ? System.nanoTime() : 0;
        }

        @Override public void close() {
            if (this == DISABLED || closed) return;
            closed = true;
            try {
                if (recording) {
                    long elapsed = System.nanoTime() - wall;
                    long allocated = allocatedNow(), consumedCpu = cpuNow();
                    var row = totals[stage.ordinal()];
                    row[0]++; row[1] += elapsed;
                    row[2] = cpuStart < 0 || consumedCpu < cpuStart || row[2] < 0 ? -1 : row[2] + consumedCpu - cpuStart;
                    row[3] = allocationStart < 0 || allocated < allocationStart || row[3] < 0 ? -1 : row[3] + allocated - allocationStart;
                }
            } catch (RuntimeException ignored) {
                // Observations must never replace a business result or mask its exception.
            } finally {
                try { if (profiling) AcademyProfiler.pop(); }
                catch (RuntimeException ignored) { }
                finally { if (restoreContext) dispatchRecorded = previousContext; }
            }
        }
    }
}
