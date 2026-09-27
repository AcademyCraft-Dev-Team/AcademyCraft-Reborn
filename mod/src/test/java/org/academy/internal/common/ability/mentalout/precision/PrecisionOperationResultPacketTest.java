package org.academy.internal.common.ability.mentalout.precision;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PrecisionOperationResultPacketTest {
    @Test
    void resultPacketRoundTripsStructuredErrorLocation() {
        var packet = new PrecisionOperationManager.ResultPacket(
                2,
                PrecisionOperationManager.FeedbackType.ERROR,
                41L,
                PrecisionGraph.Diagnostic.CLIENT_TIMEOUT,
                17,
                1,
                3
        );
        var buffer = Unpooled.buffer();

        PrecisionOperationManager.ResultPacket.CODEC.encode(buffer, packet);
        var decoded = PrecisionOperationManager.ResultPacket.CODEC.decode(buffer);

        assertEquals(2, decoded.slot());
        assertEquals(PrecisionOperationManager.FeedbackType.ERROR, decoded.type());
        assertEquals(41L, decoded.revision());
        assertEquals(PrecisionGraph.Diagnostic.CLIENT_TIMEOUT, decoded.diagnostic());
        assertEquals(17, decoded.nodeId());
        assertEquals(1, decoded.port());
        assertEquals(3, decoded.affectedCount());
    }

    @Test
    void savePacketRoundTripsOneProgramAndRevision() {
        var packet = new PrecisionOperationManager.SavePacket(3, 91L, new byte[]{4, 8, 15, 16});
        var buffer = Unpooled.buffer();

        PrecisionOperationManager.SavePacket.CODEC.encode(buffer, packet);
        var decoded = PrecisionOperationManager.SavePacket.CODEC.decode(buffer);

        assertEquals(3, decoded.slot());
        assertEquals(91L, decoded.expectedRevision());
        assertArrayEquals(new byte[]{4, 8, 15, 16}, decoded.program());
    }

    @Test
    void syncPacketRoundTripsTheWholeBook() {
        var packet = new PrecisionOperationManager.SyncPacket(new byte[]{23, 42, 108});
        var buffer = Unpooled.buffer();

        PrecisionOperationManager.SyncPacket.CODEC.encode(buffer, packet);
        var decoded = PrecisionOperationManager.SyncPacket.CODEC.decode(buffer);

        assertArrayEquals(new byte[]{23, 42, 108}, decoded.book());
    }
}
