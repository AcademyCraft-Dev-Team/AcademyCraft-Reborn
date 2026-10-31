package org.academy.internal.common.ability.program;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramRunStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramRunStatusCodecTest {
    private static final ProgramRunStatus.Target TARGET = new ProgramRunStatus.Target(
            Identifier.parse("minecraft:overworld"), Identifier.parse("academy:accelerator"), 9, UUID.randomUUID(), 42);

    @Test void allPhasesAndClocksRoundTripWithoutInventingAnEta() {
        for (var phase : ProgramRunStatus.Phase.values()) for (var wait : ProgramRunStatus.WaitKind.values()) {
            var original = new ProgramRunStatus(UUID.randomUUID(), TARGET, 19, 72, phase, wait,
                    4, 2, Identifier.parse("academy:program/control/wait"), 7, 107, 60_000, 900,
                    "TARGET_INVALID", "structure", "retry_action_pending", true);
            var buffer = Unpooled.buffer();
            try {
                ProgramRunStatusCodec.CODEC.encode(buffer, original);
                assertTrue(buffer.readableBytes() < 1024);
                assertEquals(original, ProgramRunStatusCodec.CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test void maximumBoundedUtf8StatusFitsOneKilobyte() {
        var id = Identifier.parse("academy:" + "x".repeat(120));
        var target = new ProgramRunStatus.Target(id, id, 0, UUID.randomUUID(), Long.MAX_VALUE);
        var status = new ProgramRunStatus(UUID.randomUUID(), target, Long.MAX_VALUE, Long.MAX_VALUE,
                ProgramRunStatus.Phase.FAILED, ProgramRunStatus.WaitKind.NONE, Integer.MAX_VALUE, -1, id,
                -1, -1, -1, Long.MAX_VALUE, "错".repeat(100), "口".repeat(100), "因".repeat(100), true);
        var buffer = Unpooled.buffer();
        try {
            ProgramRunStatusCodec.CODEC.encode(buffer, status);
            assertTrue(buffer.readableBytes() <= ProgramRunStatusCodec.MAX_BYTES);
            assertEquals(status, ProgramRunStatusCodec.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void maliciousTargetLengthAndInvalidSlotAreRejectedBeforeAllocationOrSubscription() {
        var buffer = Unpooled.buffer();
        try {
            ByteBufCodecs.VAR_INT.encode(buffer, Integer.MAX_VALUE);
            assertThrows(DecoderException.class, () -> ProgramRunStatusCodec.readTarget(buffer));
            buffer.clear();
            ProgramRunStatusCodec.writeTarget(buffer, TARGET);
            int slotOffset = 1 + TARGET.dimension().toString().length() + 1 + TARGET.category().toString().length();
            buffer.setByte(slotOffset, 10);
            assertThrows(DecoderException.class, () -> ProgramRunStatusCodec.readTarget(buffer));
        } finally { buffer.release(); }
    }

    @Test void unknownPhaseIsRejectedInsteadOfBeingPresentedAsSuccess() {
        var buffer = Unpooled.buffer();
        try {
            ProgramRunStatusCodec.writeUuid(buffer, UUID.randomUUID());
            ProgramRunStatusCodec.writeTarget(buffer, TARGET);
            buffer.writeLong(1); buffer.writeLong(1); buffer.writeByte(255);
            assertThrows(DecoderException.class, () -> ProgramRunStatusCodec.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
