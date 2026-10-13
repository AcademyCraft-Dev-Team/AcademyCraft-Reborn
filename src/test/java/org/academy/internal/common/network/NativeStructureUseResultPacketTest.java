package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureUseResultPacketTest {
    @Test
    void everyOutcomeIncludesOnlyTheRequestAndResult() {
        for (var status : NativeStructureUseResultPacket.Status.values()) {
            var buffer = Unpooled.buffer();
            try {
                NativeStructureUseResultPacket.CODEC.encode(buffer, new NativeStructureUseResultPacket(19, status));
                assertEquals(9, buffer.readableBytes());
                var result = NativeStructureUseResultPacket.CODEC.decode(buffer);
                assertEquals(19, result.requestId());
                assertEquals(status, result.status());
                assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
    }

    @Test
    void rejectsInvalidIdentityStatusTruncationAndExtraData() {
        for (long id : new long[]{0, -1, Long.MIN_VALUE}) {
            var buffer = Unpooled.buffer().writeLong(id).writeByte(0);
            try {
                assertThrows(IllegalArgumentException.class, () -> NativeStructureUseResultPacket.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
        for (int status : new int[]{NativeStructureUseResultPacket.Status.values().length, 255}) {
            var buffer = Unpooled.buffer().writeLong(1).writeByte(status);
            try {
                assertThrows(IllegalArgumentException.class, () -> NativeStructureUseResultPacket.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
        var truncated = Unpooled.buffer().writeLong(1);
        try {
            assertThrows(IndexOutOfBoundsException.class, () -> NativeStructureUseResultPacket.CODEC.decode(truncated));
        } finally { truncated.release(); }
        var extra = Unpooled.buffer().writeLong(1).writeByte(0).writeByte(0);
        try {
            assertThrows(IllegalArgumentException.class, () -> NativeStructureUseResultPacket.CODEC.decode(extra));
        } finally { extra.release(); }
    }
}
