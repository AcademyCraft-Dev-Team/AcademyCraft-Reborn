package org.academy.api.common.ability;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevSyncPacketTest {
    @Test
    void carriesDevelopmentTargetAcrossUiNavigation() {
        var packet = new DevSyncPacket(
                DevState.DEVELOPING,
                0.45f,
                "academy:railgun",
                "Developing... 45%"
        );

        var buffer = Unpooled.buffer();
        DevSyncPacket.CODEC.encode(buffer, packet);
        var decoded = DevSyncPacket.CODEC.decode(buffer);

        assertEquals(DevState.DEVELOPING, decoded.getState());
        assertEquals(0.45f, decoded.getProgress());
        assertEquals("academy:railgun", decoded.getTargetId());
        assertEquals("Developing... 45%", decoded.getMessage());
    }
}
