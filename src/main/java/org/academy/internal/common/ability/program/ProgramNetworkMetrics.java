package org.academy.internal.common.ability.program;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.profiler.AcademyProfiler;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.packet.Packet;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Supplier;

/** Opt-in fixed-cardinality program-packet measurements; never retains packets, players or buffers. */
public final class ProgramNetworkMetrics {
    public enum Kind {
        REQUEST, IMPORT, SAVE, EXECUTE, DISMISS_STARTER, SYNC, RESULT, OTHER, OBSERVE_RUN, RUN_STATUS;
        private final String label = name().toLowerCase(Locale.ROOT);
        public String label() { return label; }
    }

    public enum Phase {
        SEND, ENCODE, DECODE, CLIENT_SYNC_APPLY, CLIENT_RESULT_APPLY, CLIENT_STATUS_APPLY;
        private final String label = name().toLowerCase(Locale.ROOT);
        public String label() { return label; }
    }

    private enum Field {
        CALLS, SUCCEEDED, FAILED, PAYLOAD_BYTES, WALL_NANOS, CPU_NANOS,
        ALLOCATED_BYTES, CPU_SAMPLES, ALLOCATION_SAMPLES;
        private final String label = name().toLowerCase(Locale.ROOT);
    }

    private static final int KIND_COUNT = Kind.values().length;
    private static final int PHASE_COUNT = Phase.values().length;
    private static final int FIELD_COUNT = Field.values().length;
    private static final String[][] ZONES = zones();
    private static final Scope DISABLED = new Scope();
    private static final ThreadLocal<Boolean> OBSERVING = new ThreadLocal<>();
    private static volatile Capture capture;

    private ProgramNetworkMetrics() {}

    /** Fixture/debug aggregation only. AcademyProfiler zones can be enabled independently. */
    public static void resetCapture(boolean enabled) { capture = enabled ? new Capture() : null; }

    public static Map<String, Object> snapshot() {
        var current = capture;
        var result = new LinkedHashMap<String, Object>();
        result.put("capture_enabled", current != null);
        result.put("payload_bytes_scope", "Application packet CODEC bytes only; excludes Misaka registry id, Minecraft framing/compression/encryption and TCP/IP");
        result.put("send_scope", "Calls to Misaka send, including synchronous wrapper construction/encoding and connection enqueue; not delivery acknowledgement");
        result.put("timing_scope", "nanoTime wall elapsed, optional current-thread CPU and heap allocation counters; nested send/encode times must not be summed");
        var rows = new ArrayList<Map<String, Object>>();
        if (current != null) {
            for (var kind : Kind.values()) for (var phase : Phase.values()) {
                int base = offset(kind, phase);
                if (current.values.get(base + Field.CALLS.ordinal()) == 0) continue;
                var row = new LinkedHashMap<String, Object>();
                row.put("kind", kind.label()); row.put("phase", phase.label());
                for (var field : Field.values()) row.put(field.label, current.values.get(base + field.ordinal()));
                rows.add(Map.copyOf(row));
            }
        }
        result.put("operations", java.util.List.copyOf(rows));
        return Map.copyOf(result);
    }

    public static Scope begin(Kind kind, Phase phase) {
        var current = capture;
        boolean profiler = AcademyProfiler.isCapturingZones();
        return current == null && !profiler || Boolean.TRUE.equals(OBSERVING.get())
                ? DISABLED : new Scope(current, kind, phase, profiler);
    }

    /** Read-only benchmark observers must not count their extra decode as an actual receive. */
    public static <T> T observeUnprofiled(Supplier<T> action) {
        var previous = OBSERVING.get();
        OBSERVING.set(true);
        try { return action.get(); }
        finally {
            if (previous == null) OBSERVING.remove(); else OBSERVING.set(previous);
        }
    }

    /** Instruments the existing codec once, without a measuring re-encode or index changes. */
    public static <P> StreamCodec<ByteBuf, P> instrument(Kind kind, StreamCodec<ByteBuf, P> codec) {
        return new StreamCodec<>() {
            @Override public void encode(ByteBuf buffer, P packet) {
                int start = buffer.writerIndex();
                try (var scope = begin(kind, Phase.ENCODE)) {
                    codec.encode(buffer, packet);
                    scope.succeeded(buffer.writerIndex() - start);
                }
            }

            @Override public P decode(ByteBuf buffer) {
                int start = buffer.readerIndex();
                try (var scope = begin(kind, Phase.DECODE)) {
                    var packet = codec.decode(buffer);
                    scope.succeeded(buffer.readerIndex() - start);
                    return packet;
                }
            }
        };
    }

    public static <P extends Packet<ClientPacketListener, P>> void send(ServerPlayer player, P packet) {
        try (var scope = begin(kind(packet), Phase.SEND)) {
            MisakaNetworkServer.send(player, packet);
            scope.succeeded(0);
        }
    }

    public static <P extends Packet<ServerGamePacketListenerImpl, P>> void sendToServer(P packet) {
        try (var scope = begin(kind(packet), Phase.SEND)) {
            MisakaNetworkClient.send(packet);
            scope.succeeded(0);
        }
    }

    public static Kind kind(Packet<?, ?> packet) {
        if (packet instanceof AbilityProgramManager.ObserveRunPacket) return Kind.OBSERVE_RUN;
        if (packet instanceof AbilityProgramManager.RunStatusPacket) return Kind.RUN_STATUS;
        if (packet instanceof AbilityProgramManager.RequestPacket) return Kind.REQUEST;
        if (packet instanceof AbilityProgramManager.ImportPacket) return Kind.IMPORT;
        if (packet instanceof AbilityProgramManager.SavePacket) return Kind.SAVE;
        if (packet instanceof AbilityProgramManager.ExecutePacket) return Kind.EXECUTE;
        if (packet instanceof AbilityProgramManager.DismissStarterPacket) return Kind.DISMISS_STARTER;
        if (packet instanceof AbilityProgramManager.SyncPacket) return Kind.SYNC;
        if (packet instanceof AbilityProgramManager.ResultPacket) return Kind.RESULT;
        return Kind.OTHER;
    }

    public static final class Scope implements AutoCloseable {
        private final Capture capture;
        private final Kind kind;
        private final Phase phase;
        private final boolean profiler;
        private final long started, cpuStarted, allocatedStarted;
        private boolean closed, succeeded;
        private long bytes;

        private Scope() {
            capture = null; kind = Kind.OTHER; phase = Phase.SEND; profiler = false;
            started = 0; cpuStarted = allocatedStarted = -1;
        }

        private Scope(Capture capture, Kind kind, Phase phase, boolean profiler) {
            this.capture = capture; this.kind = kind; this.phase = phase; this.profiler = profiler;
            if (profiler) AcademyProfiler.push(ZONES[kind.ordinal()][phase.ordinal()]);
            cpuStarted = Sampler.cpu(); allocatedStarted = Sampler.allocated(); started = System.nanoTime();
        }

        public void succeeded(long payloadBytes) {
            if (started == 0) return;
            succeeded = true; bytes = Math.max(0, payloadBytes);
        }

        @Override public void close() {
            if (started == 0 || closed) return;
            closed = true;
            try {
                long elapsed = System.nanoTime() - started;
                long cpu = Sampler.cpu(), allocated = Sampler.allocated();
                record(Field.CALLS, 1); record(succeeded ? Field.SUCCEEDED : Field.FAILED, 1);
                record(Field.PAYLOAD_BYTES, bytes); record(Field.WALL_NANOS, elapsed);
                if (cpuStarted >= 0 && cpu >= cpuStarted) {
                    record(Field.CPU_NANOS, cpu - cpuStarted); record(Field.CPU_SAMPLES, 1);
                }
                if (allocatedStarted >= 0 && allocated >= allocatedStarted) {
                    record(Field.ALLOCATED_BYTES, allocated - allocatedStarted); record(Field.ALLOCATION_SAMPLES, 1);
                }
            } finally {
                if (profiler) AcademyProfiler.pop();
            }
        }

        private void record(Field field, long value) {
            if (capture != null) capture.values.addAndGet(offset(kind, phase) + field.ordinal(), value);
            if (profiler && value > 0) {
                // Preserve large counters without narrowing or creating dynamic labels.
                long remaining = value;
                while (remaining > Integer.MAX_VALUE) {
                    AcademyProfiler.incrementCounter(field.label, Integer.MAX_VALUE); remaining -= Integer.MAX_VALUE;
                }
                AcademyProfiler.incrementCounter(field.label, (int) remaining);
            }
        }
    }

    private static final class Capture {
        private final AtomicLongArray values = new AtomicLongArray(KIND_COUNT * PHASE_COUNT * FIELD_COUNT);
    }

    private static final class Sampler {
        private static final java.lang.management.ThreadMXBean CPU = bean();
        private static java.lang.management.ThreadMXBean bean() {
            try { return ManagementFactory.getThreadMXBean(); }
            catch (SecurityException ignored) { return null; }
        }
        private static long cpu() {
            try {
                return CPU != null && CPU.isCurrentThreadCpuTimeSupported() && CPU.isThreadCpuTimeEnabled()
                        ? CPU.getCurrentThreadCpuTime() : -1;
            } catch (UnsupportedOperationException | SecurityException ignored) { return -1; }
        }
        private static long allocated() {
            try {
                return CPU instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()
                        && bean.isThreadAllocatedMemoryEnabled() ? bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
            } catch (UnsupportedOperationException | SecurityException ignored) { return -1; }
        }
    }

    private static int offset(Kind kind, Phase phase) {
        return (kind.ordinal() * PHASE_COUNT + phase.ordinal()) * FIELD_COUNT;
    }

    private static String[][] zones() {
        var result = new String[KIND_COUNT][PHASE_COUNT];
        for (var kind : Kind.values()) for (var phase : Phase.values()) {
            result[kind.ordinal()][phase.ordinal()] = "program.network." + phase.label() + "." + kind.label();
        }
        return result;
    }
}
