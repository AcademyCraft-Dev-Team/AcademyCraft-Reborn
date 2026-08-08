package org.academy.internal.common.attribute;

import io.netty.buffer.Unpooled;
import org.academy.api.common.attribute.AbilityFactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropsPacketsTest {
    @Test
    void syncPacketRoundTripsAtomicState() {
        var packet = new PropsPackets.SyncPacket(new double[]{1.0, 2.0, 3.0, 4.0, 5.0}, 0b10101, true);
        var buffer = Unpooled.buffer();
        PropsPackets.SyncPacket.CODEC.encode(buffer, packet);
        var decoded = PropsPackets.SyncPacket.CODEC.decode(buffer);

        assertArrayEquals(new double[]{1.0, 2.0, 3.0, 4.0, 5.0}, decoded.values());
        assertEquals(0b10101, decoded.lockedMask());
        assertTrue(decoded.started());
    }

    @Test
    void lockPacketPreservesFactorAndState() {
        var packet = new PropsPackets.SetLockPacket(AbilityFactor.NEURAL_ACTIVITY, true);
        var buffer = Unpooled.buffer();
        PropsPackets.SetLockPacket.CODEC.encode(buffer, packet);
        var decoded = PropsPackets.SetLockPacket.CODEC.decode(buffer);

        assertEquals(AbilityFactor.NEURAL_ACTIVITY.ordinal(), decoded.factorOrdinal());
        assertTrue(decoded.locked());
    }
}
