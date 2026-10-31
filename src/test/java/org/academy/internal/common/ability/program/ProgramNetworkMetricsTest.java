package org.academy.internal.common.ability.program;

import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.profiler.AcademyProfiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramNetworkMetricsTest {
    @AfterEach void stop() { ProgramNetworkMetrics.resetCapture(false); AcademyProfiler.stopZoneCapture(); }

    @Test void countsActualCodecBytesOnceWithoutMovingOtherIndices() {
        var encodes = new AtomicInteger(); var decodes = new AtomicInteger();
        var raw = StreamCodec.<io.netty.buffer.ByteBuf, Integer>of((buffer, value) -> {
            encodes.incrementAndGet(); buffer.writeInt(value);
        }, buffer -> { decodes.incrementAndGet(); return buffer.readInt(); });
        var codec = ProgramNetworkMetrics.instrument(ProgramNetworkMetrics.Kind.SAVE, raw);
        ProgramNetworkMetrics.resetCapture(true);
        var buffer = Unpooled.buffer();
        try {
            buffer.writeByte(7); codec.encode(buffer, 42);
            assertEquals(5, buffer.writerIndex()); assertEquals(0, buffer.readerIndex());
            assertEquals(7, buffer.readUnsignedByte()); assertEquals(42, codec.decode(buffer));
            assertFalse(buffer.isReadable()); assertEquals(1, encodes.get()); assertEquals(1, decodes.get());
            for (var phase : List.of("encode", "decode")) {
                var row = row("save", phase);
                assertEquals(1L, row.get("calls")); assertEquals(1L, row.get("succeeded"));
                assertEquals(0L, row.get("failed")); assertEquals(4L, row.get("payload_bytes"));
            }
        } finally { buffer.release(); }
    }

    @Test void disabledAndReadOnlyObserverPathsDoNotRecordSyntheticReceives() {
        var codec = ProgramNetworkMetrics.instrument(ProgramNetworkMetrics.Kind.RESULT,
                StreamCodec.<io.netty.buffer.ByteBuf, Integer>of((buffer, value) -> buffer.writeInt(value), buffer -> buffer.readInt()));
        ProgramNetworkMetrics.resetCapture(false);
        var buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, 1); assertEquals(1, codec.decode(buffer)); assertTrue(operations().isEmpty());
            ProgramNetworkMetrics.resetCapture(true); buffer.clear(); codec.encode(buffer, 2);
            assertEquals(2, ProgramNetworkMetrics.observeUnprofiled(() -> codec.decode(buffer)));
            assertEquals(1, operations().size()); assertEquals("encode", operations().getFirst().get("phase"));
            assertThrows(IllegalArgumentException.class, () -> ProgramNetworkMetrics.observeUnprofiled(() -> {
                throw new IllegalArgumentException("observer");
            }));
            buffer.clear(); codec.encode(buffer, 3);
            assertEquals(2L, row("result", "encode").get("calls"));
        } finally { buffer.release(); }
    }

    @Test void codecFailureKeepsTheOriginalExceptionAndPartialWriteBehavior() {
        var failure = new IllegalArgumentException("original");
        var codec = ProgramNetworkMetrics.instrument(ProgramNetworkMetrics.Kind.IMPORT,
                StreamCodec.<io.netty.buffer.ByteBuf, Integer>of((buffer, value) -> {
                    buffer.writeByte(1); throw failure;
                }, buffer -> { buffer.readByte(); throw failure; }));
        ProgramNetworkMetrics.resetCapture(true);
        AcademyProfiler.startZoneCapture();
        var buffer = Unpooled.buffer();
        try {
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> codec.encode(buffer, 4)));
            assertEquals(1, buffer.writerIndex());
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer)));
            assertEquals(1, buffer.readerIndex());
            for (var phase : List.of("encode", "decode")) {
                assertEquals(1L, row("import", phase).get("failed"));
                assertEquals(0L, row("import", phase).get("payload_bytes"));
            }
            var thread = AcademyProfiler.snapshot().getZones().get(Thread.currentThread().getName());
            assertNotNull(thread);
            assertEquals(2, thread.childrenOf("root").stream()
                    .filter(value -> value.name().startsWith("program.network.")).count(),
                    "A failing codec must close its profiler scope before the next codec starts");
        } finally { buffer.release(); }
    }

    @SuppressWarnings("unchecked") private static List<Map<String, Object>> operations() {
        return (List<Map<String, Object>>) ProgramNetworkMetrics.snapshot().get("operations");
    }
    private static Map<String, Object> row(String kind, String phase) {
        return operations().stream().filter(value -> kind.equals(value.get("kind")) && phase.equals(value.get("phase")))
                .findFirst().orElseThrow();
    }
}
