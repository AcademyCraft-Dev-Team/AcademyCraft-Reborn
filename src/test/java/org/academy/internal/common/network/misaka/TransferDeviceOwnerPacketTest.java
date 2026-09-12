package org.academy.internal.common.network.misaka;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferDeviceOwnerPacketTest {
    @Test
    void codecRoundTripsPosAndName() {
        var pos = new BlockPos(12, 64, -7);
        var original = new TransferDeviceOwnerPacket(pos, "Misaka");
        var buf = Unpooled.buffer();
        TransferDeviceOwnerPacket.CODEC.encode(buf, original);
        var decoded = TransferDeviceOwnerPacket.CODEC.decode(buf);
        assertEquals(pos, decoded.devicePos());
        assertEquals("Misaka", decoded.targetPlayerName());
    }
}
