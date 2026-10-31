package org.academy.internal.common.ability.program;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ProgramNetworkPacketBoundsTest {
    @AfterEach void stop() { ProgramNetworkMetrics.resetCapture(false); }

    @Test void aFullTraceAndMainDiagnosticKeepTheirDifferentBoundsAndRevision() {
        var steps = new ArrayList<ProgramRunTrace.Step>();
        for (int index = 0; index < 49; index++) {
            steps.add(new ProgramRunTrace.Step(index, "f".repeat(80), "d".repeat(300)));
        }
        var mainDetail = "main diagnostic ".repeat(100);
        var packet = new AbilityProgramManager.ResultPacket("academy:accelerator", 9,
                AbilityProgramManager.FeedbackType.TRACE, 712, AbilityProgramManager.ResultCode.EXECUTION_FAILED,
                null, 47, ProgramVmDiagnostic.ACTION_REJECTED, "structure", mainDetail, steps, false);
        var buffer = Unpooled.buffer();
        try {
            ProgramNetworkMetrics.resetCapture(true);
            AbilityProgramManager.ResultPacket.CODEC.encode(buffer, packet);
            var decoded = AbilityProgramManager.ResultPacket.CODEC.decode(buffer);
            assertEquals(48, decoded.trace().size()); assertTrue(decoded.traceTruncated());
            assertEquals(32, decoded.trace().getFirst().flowOutput().length());
            assertEquals(120, decoded.trace().getFirst().detail().length());
            assertEquals(mainDetail, decoded.detail(), "Main packet detail must not inherit the trace-step bound");
            assertEquals(712, decoded.revision()); assertEquals("structure", decoded.port());
            assertEquals(AbilityProgramManager.ResultCode.EXECUTION_FAILED, decoded.code());
            assertEquals(ProgramVmDiagnostic.ACTION_REJECTED, decoded.vmDiagnostic());
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void rejectsAnOversizedWireTraceCountBeforeReadingAnySteps() {
        var buffer = Unpooled.buffer();
        try {
            ByteBufCodecs.STRING_UTF8.encode(buffer, "academy:accelerator");
            ByteBufCodecs.VAR_INT.encode(buffer, 0);
            ByteBufCodecs.VAR_INT.encode(buffer, AbilityProgramManager.FeedbackType.TRACE.ordinal());
            buffer.writeLong(1);
            ByteBufCodecs.VAR_INT.encode(buffer, AbilityProgramManager.ResultCode.OK.ordinal());
            ByteBufCodecs.VAR_INT.encode(buffer, -1); ByteBufCodecs.VAR_INT.encode(buffer, -1);
            ByteBufCodecs.VAR_INT.encode(buffer, ProgramVmDiagnostic.NONE.ordinal());
            ByteBufCodecs.STRING_UTF8.encode(buffer, ""); ByteBufCodecs.STRING_UTF8.encode(buffer, "");
            ByteBufCodecs.VAR_INT.encode(buffer, ProgramRunTrace.MAX_STEPS + 1);
            ProgramNetworkMetrics.resetCapture(true);
            assertThrows(DecoderException.class, () -> AbilityProgramManager.ResultPacket.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void programAndBookPayloadBudgetsArePreservedByMetricsWrappers() {
        var category = Identifier.parse("academy:accelerator");
        var buffer = Unpooled.buffer();
        try {
            ProgramNetworkMetrics.resetCapture(true);
            assertThrows(EncoderException.class, () -> AbilityProgramManager.SavePacket.CODEC.encode(buffer,
                    new AbilityProgramManager.SavePacket(category, 0, 42, new byte[ProgramBookCodec.MAX_PROGRAM_ENCODED_BYTES + 1])));
            buffer.clear();
            assertThrows(EncoderException.class, () -> AbilityProgramManager.ImportPacket.CODEC.encode(buffer,
                    new AbilityProgramManager.ImportPacket(category, 42, new byte[ProgramBookCodec.MAX_BOOK_ENCODED_BYTES + 1])));
            buffer.clear();
            var source = new byte[]{1, 2, 3};
            var packet = new AbilityProgramManager.SavePacket(category, 7, 42, source);
            source[0] = 9;
            AbilityProgramManager.SavePacket.CODEC.encode(buffer, packet);
            var decoded = AbilityProgramManager.SavePacket.CODEC.decode(buffer);
            assertEquals(42, decoded.expectedRevision()); assertEquals(7, decoded.slot());
            assertArrayEquals(new byte[]{1, 2, 3}, decoded.program());
        } finally { buffer.release(); }
    }
}
