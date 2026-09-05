package org.academy.internal.common.network.misaka;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaNetManageDataPacketCoverageTest {
    @Test
    void codecRoundTripsInCoverage() {
        var original = new MisakaNetManageDataPacket(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                0,
                2,
                12.5f,
                List.of(
                        new MisakaNetManageDataPacket.SisterSummary(1, 50, 50f, "node-a", false, true),
                        new MisakaNetManageDataPacket.SisterSummary(2, 80, 0f, "node-a", true, false)
                ),
                new int[]{40, 0, 0, 0}
        );
        var buf = Unpooled.buffer();
        MisakaNetManageDataPacket.CODEC.encode(buf, original);
        var decoded = MisakaNetManageDataPacket.CODEC.decode(buf);
        assertEquals(2, decoded.sisters().size());
        assertTrue(decoded.sisters().get(0).inCoverage());
        assertEquals(50f, decoded.sisters().get(0).msk(), 0.001f);
        assertFalse(decoded.sisters().get(1).inCoverage());
        assertEquals(0f, decoded.sisters().get(1).msk(), 0.001f);
        assertTrue(decoded.sisters().get(1).starving());
    }
}
