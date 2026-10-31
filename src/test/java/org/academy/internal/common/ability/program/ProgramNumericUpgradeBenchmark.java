package org.academy.internal.common.ability.program;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.compile.CompiledProgram;
import org.academy.internal.common.ability.program.compile.ProgramCompiler;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.ProgramExecutorLookup;
import org.academy.internal.common.ability.program.registry.ProgramNodeExecutor;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in actual-VM benchmark. Compilation and correctness signatures are outside timed samples. */
class ProgramNumericUpgradeBenchmark {
    private static final Identifier CATEGORY = AcademyCraft.academy("accelerator");
    private static final int WARMUP = 32, SAMPLES = 128, BATCH = 4;
    private static volatile Object blackhole;

    @Test
    void measureLegacyAndExplicitNumericGraphs() throws Exception {
        var destination = System.getProperty("academy.numericUpgradeBenchmarkReport");
        if (destination == null || destination.isBlank()) return;
        var definition = AbilityProgramDefinitions.require(CATEGORY);
        var report = new LinkedHashMap<String, Object>();
        var cases = new ArrayList<Map<String, Object>>();
        var bean = ManagementFactory.getThreadMXBean();
        for (int size : List.of(2, 32, 128)) {
            for (String kind : size == 2 ? List.of("none") : List.of("integer", "big_integer", "float")) {
                String previousSignature = null;
                for (boolean upgraded : List.of(false, true)) {
                    var graph = graph(size, kind, upgraded);
                    var compiled = ProgramCompiler.compile(graph, new ProgramCompileContext(CATEGORY, Set.of(), ProgramLimits.DEFAULT), definition.nodeLookup());
                    assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
                    var program = Objects.requireNonNull(compiled.program());
                    String signature = characterize(program, kind, size, definition.executors());
                    if (previousSignature != null) assertEquals(previousSignature, signature, "old/new observable VM outcomes differ");
                    previousSignature = signature;
                    for (int warm = 0; warm < WARMUP * BATCH; warm++) blackhole = execute(program, definition.executors(), 16384);
                    var nanos = new long[SAMPLES]; var allocations = new long[SAMPLES];
                    for (int sample = 0; sample < SAMPLES; sample++) {
                        long beforeAllocation = allocated(bean), start = System.nanoTime();
                        Run last = null;
                        for (int index = 0; index < BATCH; index++) last = execute(program, definition.executors(), 16384);
                        nanos[sample] = (System.nanoTime() - start) / BATCH;
                        long afterAllocation = allocated(bean);
                        allocations[sample] = beforeAllocation < 0 || afterAllocation < beforeAllocation ? -1 : (afterAllocation - beforeAllocation) / BATCH;
                        assertNotNull(last); assertEquals(ProgramVmResult.Status.COMPLETED, last.result.status());
                        blackhole = last;
                    }
                    cases.add(Map.of("nodes", size, "edges", graph.edges().size(), "kind", kind,
                            "variant", upgraded ? "explicit_numeric" : "legacy", "signature", signature,
                            "wall_nanos_per_session", distribution(nanos), "thread_allocated_bytes_per_session", distribution(allocations)));
                }
            }
        }
        report.put("passed", true); report.put("stage", System.getProperty("academy.numericUpgradeBenchmarkStage", "baseline"));
        report.put("base_commit", "195ed8c6"); report.put("java", System.getProperty("java.runtime.version"));
        report.put("warmup_sessions_per_case", WARMUP * BATCH); report.put("samples", SAMPLES); report.put("batch_sessions", BATCH);
        report.put("cases", cases); report.put("source_sha256", sourceHashes());
        report.put("scope", List.of("Fresh real ProgramVm.Session and ProgramExecutionFrame, actual node resolveConfiguration and registered executors",
                "Graphs compiled directly before measurement, without migration or compile cache; 2-node entry/STOP is the no-arithmetic control",
                "32/128-node typed addition DAGs plus session variable sink; scalar sources identical in legacy/explicit variants",
                "nanoTime wall duration and current-thread Java allocated bytes, not CPU/TPS or multiplayer/physics throughput",
                "Correctness, exact fuel boundary, signature formatting and JSON are outside timed samples; default profiling remains off",
                "No world query, random source, paid action or WAIT executes in this timing corpus; independent equivalence tests cover those semantics",
                "Both pre-optimization source snapshots and optimized runs are required before drawing a performance conclusion"));
        var path = Path.of(destination).toAbsolutePath().normalize();
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }

    private static String characterize(CompiledProgram program, String kind, int size, ProgramExecutorLookup executors) {
        var calls = new int[]{0};
        ProgramExecutorLookup counted = id -> counting(executors.find(id), calls);
        var run = execute(program, counted, size);
        assertEquals(ProgramVmResult.Status.COMPLETED, run.result.status());
        assertEquals(size - 1, calls[0], "Each non-entry node should execute once at this exact fuel boundary");
        assertEquals(0, run.transaction.size(), "Pure arithmetic must stage no paid action");
        var lowFuel = execute(program, executors, size - 1);
        assertEquals(ProgramVmResult.Status.FUEL_EXHAUSTED, lowFuel.result.status());
        var resumed = lowFuel.session.run(1, 16384, executors, lowFuel.frame);
        assertEquals(ProgramVmResult.Status.COMPLETED, resumed.status());
        assertEquals(run.session.variables(), lowFuel.session.variables());
        if (size > 2) {
            var value = run.session.variables().get("answer");
            assertNotNull(value); assertEquals(type(kind), value.type());
            long expected = 1L + 2L * (size - 4);
            assertEquals(expected, ((Number) value.value()).longValue());
        }
        return run.result.status() + ":" + run.result.nodeId() + ":" + calls[0] + ":" + run.session.variables();
    }

    private static <C> ProgramNodeExecutor<C> counting(ProgramNodeExecutor<C> original, int[] count) {
        return (context, config, inputs) -> { count[0]++; return original.execute(context, config, inputs); };
    }
    private record Run(ProgramVm.Session session, ProgramVmResult result, ProgramExecutionFrame frame, ProgramActionTransaction transaction) {}
    private static Run execute(CompiledProgram program, ProgramExecutorLookup executors, int fuel) {
        var session = new ProgramVm.Session(program); var transaction = ProgramActionTransaction.sequential();
        var frame = new ProgramExecutionFrame(transaction, null);
        return new Run(session, session.run(0, fuel, executors, frame), frame, transaction);
    }
    private static ProgramGraph graph(int count, String kind, boolean upgraded) {
        var nodes = new ArrayList<ProgramGraph.Node>(); var edges = new ArrayList<ProgramGraph.Edge>();
        nodes.add(node(0, BaseAbilityProgramDefinition.entryId(CATEGORY), new JsonObject()));
        if (count == 2) {
            nodes.add(node(1, CommonProgramNodeIds.STOP, new JsonObject())); edges.add(edge(0, "flow", 1, "flow"));
            return new ProgramGraph(nodes, edges);
        }
        var variable = new JsonObject(); variable.addProperty("name", "answer"); variable.addProperty("type", type(kind).id().toString());
        nodes.add(node(1, CommonProgramNodeIds.VARIABLE_SET, variable));
        for (int id : List.of(2, 3)) {
            var config = new JsonObject(); config.addProperty("type", kind); config.addProperty("value", id == 2 ? "1" : "2");
            nodes.add(node(id, CommonProgramNodeIds.SCALAR_CONSTANT, config));
        }
        int previous = 2; String output = "value";
        for (int id = 4; id < count; id++) {
            var config = new JsonObject();
            if (upgraded) { config.addProperty("type", kind); config.addProperty("operator", "add"); }
            var legacy = switch (kind) {
                case "integer" -> CommonProgramNodeIds.INTEGER_ADD; case "big_integer" -> CommonProgramNodeIds.BIG_INTEGER_ADD;
                case "float" -> CommonProgramNodeIds.FLOAT_ADD; default -> throw new IllegalArgumentException();
            };
            nodes.add(node(id, upgraded ? CommonProgramNodeIds.NUMERIC_ARITHMETIC : legacy, config));
            edges.add(edge(previous, output, id, "left")); edges.add(edge(3, "value", id, "right"));
            previous = id; output = "result";
        }
        edges.add(edge(0, "flow", 1, "flow")); edges.add(edge(previous, output, 1, "value"));
        return new ProgramGraph(nodes, edges);
    }
    private static ProgramValueType type(String kind) {
        return switch (kind) { case "integer" -> ProgramValueTypes.INTEGER; case "big_integer" -> ProgramValueTypes.BIG_INTEGER;
            case "float" -> ProgramValueTypes.FLOAT; default -> throw new IllegalArgumentException(); };
    }
    private static ProgramGraph.Node node(int id, Identifier type, JsonObject config) { return new ProgramGraph.Node(id, type, 1, config); }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
    private static long allocated(java.lang.management.ThreadMXBean bean) {
        try { return bean instanceof com.sun.management.ThreadMXBean allocation && allocation.isThreadAllocatedMemorySupported()
                && allocation.isThreadAllocatedMemoryEnabled() ? allocation.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1; }
        catch (UnsupportedOperationException | SecurityException unavailable) { return -1; }
    }
    private static Map<String, Object> distribution(long[] input) {
        var values = input.clone(); Arrays.sort(values);
        if (values[0] < 0) return Map.of("available", false, "samples", values.length);
        return Map.of("available", true, "samples", values.length, "p50", values[(int) Math.ceil(values.length * .5) - 1],
                "p95", values[(int) Math.ceil(values.length * .95) - 1], "p99", values[(int) Math.ceil(values.length * .99) - 1],
                "min", values[0], "max", values[values.length - 1]);
    }
    private static Map<String, String> sourceHashes() throws Exception {
        var hashes = new LinkedHashMap<String, String>();
        for (String file : List.of("src/main/java/org/academy/internal/common/ability/program/CommonProgramExecutors.java",
                "src/main/java/org/academy/internal/common/ability/program/registry/CommonProgramNodeCatalog.java",
                "src/main/java/org/academy/internal/common/ability/program/ProgramVm.java")) {
            hashes.put(file, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(file)))));
        }
        return hashes;
    }
}
