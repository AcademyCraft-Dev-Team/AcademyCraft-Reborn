package org.academy.internal.common.network.misaka;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaNetManageDataPacketCoverageTest {
    private static final UUID SISTER_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SISTER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void codecRoundTripsInCoverage() {
        var original = new MisakaNetManageDataPacket(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                0,
                2,
                12.5f,
                8.0f,
                6.5f,
                0.8125f,
                5.0f,
                1.5f,
                List.of(
                        new MisakaNetManageDataPacket.SisterSummary(
                                SISTER_A, 1, 50, 50f, "node-a", false, false, true),
                        new MisakaNetManageDataPacket.SisterSummary(
                                SISTER_B, 2, 80, 0f, "node-a", true, false, false)
                ),
                new int[]{40, 0, 0, 0},
                List.of("alice", "bob"),
                List.of(new MisakaNetManageDataPacket.MemberSummary(
                        "alice", true, List.of("ADMIN", "ACCESS"))),
                true
        );
        var buf = Unpooled.buffer();
        MisakaNetManageDataPacket.CODEC.encode(buf, original);
        var decoded = MisakaNetManageDataPacket.CODEC.decode(buf);
        assertEquals(2, decoded.sisters().size());
        assertEquals(SISTER_A, decoded.sisters().get(0).misakaUuid());
        assertEquals(SISTER_B, decoded.sisters().get(1).misakaUuid());
        assertTrue(decoded.sisters().get(0).inCoverage());
        assertEquals(50f, decoded.sisters().get(0).msk(), 0.001f);
        assertFalse(decoded.sisters().get(1).inCoverage());
        assertEquals(0f, decoded.sisters().get(1).msk(), 0.001f);
        assertTrue(decoded.sisters().get(1).starving());
        assertFalse(decoded.sisters().get(0).incapacitated());
        assertFalse(decoded.sisters().get(1).incapacitated());
        assertEquals(12.5f, decoded.totalMskPerSecond(), 0.001f);
        assertEquals(8.0f, decoded.totalDemandMsk(), 0.001f);
        assertEquals(6.5f, decoded.yourAllocatedMsk(), 0.001f);
        assertEquals(0.8125f, decoded.yourSatisfaction(), 0.001f);
        assertEquals(5.0f, decoded.priorityAllocatedMsk(), 0.001f);
        assertEquals(1.5f, decoded.sharedAllocatedMsk(), 0.001f);
        assertEquals(List.of("alice", "bob"), decoded.adminNames());
        assertEquals(1, decoded.members().size());
        assertTrue(decoded.viewerCanEditMembers());
    }
}
