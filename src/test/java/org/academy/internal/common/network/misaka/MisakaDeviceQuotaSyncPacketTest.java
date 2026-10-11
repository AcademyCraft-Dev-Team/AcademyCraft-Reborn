package org.academy.internal.common.network.misaka;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaDeviceQuotaSyncPacketTest {
    @Test
    void codecRoundTrips() {
        var id = Identifier.parse("academy:laser_designator");
        var original = new MisakaDeviceQuotaSyncPacket(
                42.5f,
                35,
                List.of(new MisakaDeviceQuotaSyncPacket.DeviceQuotaRow(id, 40, 60, true))
        );
        var buf = Unpooled.buffer();
        MisakaDeviceQuotaSyncPacket.CODEC.encode(buf, original);
        var decoded = MisakaDeviceQuotaSyncPacket.CODEC.decode(buf);
        assertEquals(42.5f, decoded.playerBudgetMskPerSec(), 0.001f);
        assertEquals(35, decoded.remainingPercentSlots());
        assertEquals(1, decoded.rows().size());
        assertEquals(id, decoded.rows().getFirst().id());
        assertEquals(40, decoded.rows().getFirst().effectivePercent());
        assertEquals(60, decoded.rows().getFirst().lastPercent());
        assertTrue(decoded.rows().getFirst().present());
    }
}
