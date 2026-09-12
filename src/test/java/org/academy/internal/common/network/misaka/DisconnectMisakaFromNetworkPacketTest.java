package org.academy.internal.common.network.misaka;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisconnectMisakaFromNetworkPacketTest {
    @Test
    void codecRoundTripsConsoleAndTarget() {
        var console = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        var target = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
        var original = new DisconnectMisakaFromNetworkPacket(console, target, 3);
        var buf = Unpooled.buffer();
        DisconnectMisakaFromNetworkPacket.CODEC.encode(buf, original);
        var decoded = DisconnectMisakaFromNetworkPacket.CODEC.decode(buf);
        assertEquals(console, decoded.misakaUuid());
        assertEquals(target, decoded.targetMisakaUuid());
        assertEquals(3, decoded.pageIndex());
    }
}
