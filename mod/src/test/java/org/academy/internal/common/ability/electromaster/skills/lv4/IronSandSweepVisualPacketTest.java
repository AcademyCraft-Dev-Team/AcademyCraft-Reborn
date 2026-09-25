package org.academy.internal.common.ability.electromaster.skills.lv4;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IronSandSweepVisualPacketTest {
    @Test
    void codecRoundTripsEntityId() {
        var expected = new IronSandArsenal.SweepVisualPacket(12345);
        var buffer = Unpooled.buffer();

        IronSandArsenal.SweepVisualPacket.CODEC.encode(buffer, expected);
        var decoded = IronSandArsenal.SweepVisualPacket.CODEC.decode(buffer);

        assertEquals(expected.entityId(), decoded.entityId());
    }
}
