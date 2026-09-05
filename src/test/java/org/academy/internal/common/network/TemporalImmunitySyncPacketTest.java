package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemporalImmunitySyncPacketTest {
    @Test
    void roundTripsFullTemporalSnapshot() {
        var session = UUID.randomUUID();
        var immuneEntity = UUID.randomUUID();
        var pausedPlayer = UUID.randomUUID();
        var packet = new TemporalImmunitySyncPacket(
                session,
                7L,
                42L,
                Map.of(immuneEntity, 3),
                Map.of(pausedPlayer, 0.0F)
        );
        var buffer = Unpooled.buffer();
        try {
            TemporalImmunitySyncPacket.CODEC.encode(buffer, packet);
            var decoded = TemporalImmunitySyncPacket.CODEC.decode(buffer);
            assertEquals(session, decoded.sessionId());
            assertEquals(7L, decoded.revision());
            assertEquals(42L, decoded.heartbeat());
            assertEquals(Map.of(immuneEntity, 3), decoded.masks());
            assertEquals(Map.of(pausedPlayer, 0.0F), decoded.playerScales());
        } finally {
            buffer.release();
        }
    }
}
