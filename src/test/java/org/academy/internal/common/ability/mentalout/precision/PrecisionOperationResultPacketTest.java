package org.academy.internal.common.ability.mentalout.precision;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrecisionOperationResultPacketTest {
    @Test
    void resultPacketRoundTripsStructuredErrorLocation() {
        var packet = new PrecisionOperationManager.ResultPacket(
                2,
                PrecisionOperationManager.FeedbackType.ERROR,
                41L,
                PrecisionGraph.Diagnostic.UNREACHABLE_DESTINATION,
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
        assertEquals(PrecisionGraph.Diagnostic.UNREACHABLE_DESTINATION, decoded.diagnostic());
        assertEquals(17, decoded.nodeId());
        assertEquals(1, decoded.port());
        assertEquals(3, decoded.affectedCount());
    }
}
