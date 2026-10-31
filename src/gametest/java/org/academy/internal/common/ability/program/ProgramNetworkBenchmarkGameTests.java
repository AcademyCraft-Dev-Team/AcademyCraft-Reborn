package org.academy.internal.common.ability.program;

import com.google.gson.GsonBuilder;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.internal.client.validation.PrecisionPerformanceGraphs;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.misaka.api.common.network.packet.Packet;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

/** Opt-in codec/envelope microbenchmark plus actual mock-Connection outbound capture; never executes a graph. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ProgramNetworkBenchmarkGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("precision_network_benchmark_function");
    private static final int WARMUP = 32, SAMPLES = 128, PROFILE_SAMPLES = 16;
    private static volatile Object blackhole;

    private ProgramNetworkBenchmarkGameTests() {}
    @SubscribeEvent private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }
    @SubscribeEvent private static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.precisionNetworkBenchmark")) return;
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("precision_network_benchmark"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 1000, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private record Payload(String name, Packet<?, ?> packet, boolean clientbound) {}
    private record Observation(int payloadBytes, int envelopeBytes, Packet<?, ?> packet) {}

    private static void run(GameTestHelper helper) {
        var report = new LinkedHashMap<String, Object>();
        var cases = new ArrayList<Map<String, Object>>();
        try {
            check(!AcademyProfiler.isCapturingZones(), "network benchmark requires separate profiler pass");
            ProgramNetworkMetrics.resetCapture(false);
            var graph = PrecisionPerformanceGraphs.editorGraph(128);
            check(graph.graph().nodes().size() == 128 && graph.graph().edges().size() == 256, "maximum structural graph boundary changed");
            var book = new ProgramBook(ProgramBook.CURRENT_SCHEMA_VERSION, 29, 9,
                    Collections.nCopies(AbilityProgramManager.SLOT_COUNT, new ProgramBook.Slot(graph)));
            var programBytes = ProgramBookCodec.encodeProgram(graph);
            var bookBytes = ProgramBookCodec.encode(book);
            check(ProgramBookCodec.decode(bookBytes).book().equals(book), "ten-slot binary book roundtrip changed");
            var payloads = corpus(graph.category(), programBytes, bookBytes);
            var meter = ManagementFactory.getThreadMXBean();
            for (var payload : payloads) {
                var expected = encode(payload.packet());
                for (var layer : List.of("application_codec_roundtrip", "misaka_envelope_roundtrip")) {
                    Supplier<Observation> operation = layer.equals("application_codec_roundtrip")
                            ? () -> payloadRoundTrip(payload.packet()) : () -> {
                                var value = ProgramNetworkEnvelopeProbe.roundTrip(payload.packet());
                                return new Observation(value.payloadBytes(), value.envelopeBytes(), value.packet());
                            };
                    for (int index = 0; index < WARMUP; index++) validate(operation.get(), expected);
                    var nanos = new long[SAMPLES]; var cpu = new long[SAMPLES]; var allocated = new long[SAMPLES];
                    Arrays.fill(cpu, -1); Arrays.fill(allocated, -1);
                    for (int index = 0; index < SAMPLES; index++) {
                        long allocatedBefore = allocated(meter), cpuBefore = cpu(meter), start = System.nanoTime();
                        var observation = operation.get();
                        nanos[index] = System.nanoTime() - start;
                        long cpuAfter = cpu(meter), allocatedAfter = allocated(meter);
                        if (cpuBefore >= 0 && cpuAfter >= cpuBefore) cpu[index] = cpuAfter - cpuBefore;
                        if (allocatedBefore >= 0 && allocatedAfter >= allocatedBefore) allocated[index] = allocatedAfter - allocatedBefore;
                        validate(observation, expected); blackhole = observation;
                    }
                    var row = new LinkedHashMap<String, Object>();
                    row.put("name", payload.name()); row.put("layer", layer); row.put("warmup", WARMUP); row.put("samples", SAMPLES);
                    row.put("payload_bytes", expected.length); row.put("payload_sha256", sha256(expected));
                    var observation = operation.get(); row.put("misaka_envelope_bytes", observation.envelopeBytes());
                    row.put("wall_nanos", distribution(nanos)); row.put("thread_cpu_nanos", distribution(cpu));
                    row.put("thread_allocated_bytes", distribution(allocated));
                    ProgramNetworkMetrics.resetCapture(true);
                    for (int index = 0; index < PROFILE_SAMPLES; index++) validate(operation.get(), expected);
                    row.put("separate_instrumented_pass_calls", PROFILE_SAMPLES);
                    row.put("metrics", ProgramNetworkMetrics.snapshot()); ProgramNetworkMetrics.resetCapture(false);
                    cases.add(row);
                }
            }
            var sends = new ArrayList<Map<String, Object>>();
            var level = helper.getLevel();
            try (var peer = CapturedProgramPlayer.create(level, UUID.nameUUIDFromBytes("precision-network-peer".getBytes(StandardCharsets.UTF_8)),
                    "codec-peer", new Vec3(32.5, level.getMaxY() - 40.5, 32.5))) {
                peer.drain(); ProgramNetworkMetrics.resetCapture(true);
                for (var payload : payloads) if (payload.clientbound()) {
                    send(peer, payload.packet());
                    var captured = peer.drain();
                    check(captured.size() == 1, "mock connection must capture one actual program send");
                    var value = captured.getFirst();
                    check(Arrays.equals(encode(payload.packet()), encode(value.packet())), "captured original send changed payload");
                    sends.add(Map.of("name", payload.name(), "kind", value.kind().label(),
                            "payload_bytes", value.payloadBytes(), "envelope_bytes", value.envelopeBytes()));
                }
                report.put("actual_mock_connection_metrics", ProgramNetworkMetrics.snapshot());
                ProgramNetworkMetrics.resetCapture(false);
            }
            report.put("passed", true); report.put("stage", System.getProperty("academy.precisionNetworkStage", "baseline"));
            report.put("base_commit", "c701bd9a"); report.put("cases", cases); report.put("actual_mock_connection_captures", sends);
            report.put("corpus", Map.of("program_nodes", 128, "program_edges", 256, "book_slots", 10,
                    "program_binary_bytes", programBytes.length, "book_binary_bytes", bookBytes.length));
            report.put("measurement_scope", List.of("Application roundtrip uses the original registered packet CODEC and Netty ByteBuf",
                    "Misaka envelope roundtrip uses real C2SPacket/S2CPacket constructor, STREAM_CODEC encode/decode, registry id and application decode",
                    "Fixture-managed wrapper buffers and temporary write copies are released explicitly; this does not change the production dependency",
                    "Mock Connection sends are registered ServerPlayer/EmbeddedChannel objects, not logged-in game clients or TCP sockets",
                    "No Minecraft outer packet-id/length framing, compression, encryption or TCP/IP bytes are counted",
                    "nanoTime is wall elapsed; current-thread CPU is separate and can be coarsely quantized; allocations exclude other threads/native memory",
                    "Packet construction and ProgramBookCodec serialization precede application roundtrip timing; envelope timing includes wrapper construction",
                    "Success/wait/failure are codec payload cases, not evidence of runtime admission, cancellation or committed transactions",
                    "Ten slots at the maximum 128-node/256-edge graph size do not claim the exact 1 MiB book byte boundary"));
        } catch (Throwable failure) {
            report.put("passed", false); report.put("failure", failure.toString()); report.put("cases", cases);
            helper.fail("Program network benchmark: " + failure);
        } finally {
            ProgramNetworkMetrics.resetCapture(false);
            try { Files.writeString(helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("precision-network-benchmark.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }
        helper.succeed();
    }

    private static List<Payload> corpus(Identifier category, byte[] program, byte[] book) {
        var trace = new ArrayList<ProgramRunTrace.Step>();
        for (int index = 0; index < 48; index++) trace.add(new ProgramRunTrace.Step(index, "flow", "value=" + "x".repeat(114)));
        return List.of(new Payload("request", new AbilityProgramManager.RequestPacket(category), false),
                new Payload("save_max_graph", new AbilityProgramManager.SavePacket(category, 9, 29, program), false),
                new Payload("import_ten_slot_book", new AbilityProgramManager.ImportPacket(category, 29, book), false),
                new Payload("execute", new AbilityProgramManager.ExecutePacket(category, 9, 1234), false),
                new Payload("sync_ten_slot_book", new AbilityProgramManager.SyncPacket(category.toString(), book, true), true),
                new Payload("result_success", new AbilityProgramManager.ResultPacket(category.toString(), 9, AbilityProgramManager.FeedbackType.COMPLETED,
                        29, AbilityProgramManager.ResultCode.OK, null, -1, ProgramVmDiagnostic.NONE), true),
                new Payload("result_waiting", new AbilityProgramManager.ResultPacket(category.toString(), 9, AbilityProgramManager.FeedbackType.DEFERRED,
                        29, AbilityProgramManager.ResultCode.OK, null, 1, ProgramVmDiagnostic.NONE, "seconds", "waiting"), true),
                new Payload("result_failed_trace48", new AbilityProgramManager.ResultPacket(category.toString(), 9, AbilityProgramManager.FeedbackType.TRACE,
                        29, AbilityProgramManager.ResultCode.EXECUTION_FAILED, null, 47, ProgramVmDiagnostic.ACTION_REJECTED,
                        "structure", "detail=" + "d".repeat(1017), trace, true), true),
                new Payload("result_revision_conflict", new AbilityProgramManager.ResultPacket(category.toString(), 9, AbilityProgramManager.FeedbackType.ERROR,
                        30, AbilityProgramManager.ResultCode.REVISION_CONFLICT, null, -1, ProgramVmDiagnostic.NONE), true));
    }

    private static void send(CapturedProgramPlayer peer, Packet<?, ?> packet) {
        if (packet instanceof AbilityProgramManager.SyncPacket value) ProgramNetworkMetrics.send(peer.player(), value);
        else if (packet instanceof AbilityProgramManager.ResultPacket value) ProgramNetworkMetrics.send(peer.player(), value);
        else throw new IllegalArgumentException("Expected clientbound packet");
    }
    @SuppressWarnings("unchecked") private static StreamCodec<ByteBuf, Packet<?, ?>> codec(Packet<?, ?> packet) {
        return (StreamCodec<ByteBuf, Packet<?, ?>>) (StreamCodec<?, ?>) packet.getPacketType().codec();
    }
    private static byte[] encode(Packet<?, ?> packet) {
        return ProgramNetworkMetrics.observeUnprofiled(() -> {
            var buffer = Unpooled.buffer();
            try { codec(packet).encode(buffer, packet); var result = new byte[buffer.readableBytes()]; buffer.readBytes(result); return result; }
            finally { buffer.release(); }
        });
    }
    private static Observation payloadRoundTrip(Packet<?, ?> packet) {
        var buffer = Unpooled.buffer();
        try { var codec = codec(packet); codec.encode(buffer, packet); int bytes = buffer.readableBytes();
            var decoded = codec.decode(buffer); check(!buffer.isReadable(), "application codec left bytes");
            return new Observation(bytes, 0, decoded);
        } finally { buffer.release(); }
    }
    private static void validate(Observation observation, byte[] expected) {
        check(observation.payloadBytes() == expected.length && Arrays.equals(expected, encode(observation.packet())), "codec payload changed");
    }
    private static long cpu(java.lang.management.ThreadMXBean bean) {
        return bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled() ? bean.getCurrentThreadCpuTime() : -1;
    }
    private static long allocated(java.lang.management.ThreadMXBean bean) {
        return bean instanceof com.sun.management.ThreadMXBean value && value.isThreadAllocatedMemorySupported()
                && value.isThreadAllocatedMemoryEnabled() ? value.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
    }
    private static Map<String, Object> distribution(long[] input) {
        var values = Arrays.stream(input).filter(value -> value >= 0).sorted().toArray();
        if (values.length == 0) return Map.of("available", false, "samples", 0);
        return Map.of("available", true, "samples", values.length, "p50", values[(int) Math.ceil(values.length * .5) - 1],
                "p95", values[(int) Math.ceil(values.length * .95) - 1], "p99", values[(int) Math.ceil(values.length * .99) - 1], "max", values[values.length - 1]);
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { ProgramNetworkBenchmarkGameTests.run(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Program packet codec/envelope and mock-Connection measurements"); }
    }
}
