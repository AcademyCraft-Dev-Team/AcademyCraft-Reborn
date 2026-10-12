package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureMiningProgressPacketTest {
    @Test
    void everyAcknowledgementRoundTripsBoundedStateWithoutNbt() {
        for (var status : NativeStructureMiningProgressPacket.Status.values()) {
            for (int stage : new int[]{-1, 0, 9}) {
                var packet = packet(stage, status);
                var buffer = Unpooled.buffer();
                try {
                    NativeStructureMiningProgressPacket.CODEC.encode(buffer, packet);
                    assertTrue(buffer.readableBytes() < 200);
                    var copy = NativeStructureMiningProgressPacket.CODEC.decode(buffer);
                    assertEquals(packet.requestId(), copy.requestId());
                    assertEquals(packet.updateSequence(), copy.updateSequence());
                    assertEquals(packet.breakerEntityId(), copy.breakerEntityId());
                    assertEquals(packet.reference(), copy.reference());
                    assertEquals(packet.runtimeId(), copy.runtimeId());
                    assertEquals(packet.localPosition(), copy.localPosition());
                    assertEquals(packet.stateId(), copy.stateId());
                    assertEquals(stage, copy.stage());
                    assertEquals(status, copy.status());
                    assertFalse(buffer.isReadable());
                } finally { buffer.release(); }
            }
        }
    }

    @Test
    void malformedEnumStageAndIdentityNeverReachTheClient() {
        for (int kind = 0; kind < 7; kind++) {
            var buffer = Unpooled.buffer();
            try {
                NativeStructureMiningProgressPacket.CODEC.encode(buffer, packet(1, NativeStructureMiningProgressPacket.Status.PROGRESS));
                switch (kind) {
                    case 0 -> buffer.setByte(buffer.writerIndex() - 1, 6);
                    case 1 -> buffer.setByte(buffer.writerIndex() - 2, 10);
                    case 2 -> buffer.setByte(buffer.writerIndex() - 2, -2);
                    case 3 -> buffer.setLong(0, 0);
                    case 4 -> buffer.setLong(8, -1);
                    case 5 -> buffer.setInt(16, -1);
                    case 6 -> buffer.writeByte(0);
                }
                assertThrows(IllegalArgumentException.class, () -> NativeStructureMiningProgressPacket.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test
    void updateMustNameAContainerCellAndBoundedStateId() {
        var base = packet(1, NativeStructureMiningProgressPacket.Status.STARTED);
        for (var position : new BlockPos[]{new BlockPos(32, 0, 0), new BlockPos(-33, 0, 0),
                new BlockPos(0, -65, 0), new BlockPos(0, 320, 0)}) {
            assertThrows(IllegalArgumentException.class, () -> new NativeStructureMiningProgressPacket(1, 1, 1,
                    base.reference(), base.runtimeId(), position, 1, 1, base.status()));
        }
        for (int state : new int[]{-1, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new NativeStructureMiningProgressPacket(1, 1, 1,
                    base.reference(), base.runtimeId(), base.localPosition(), state, 1, base.status()));
        }
    }

    private static NativeStructureMiningProgressPacket packet(int stage, NativeStructureMiningProgressPacket.Status status) {
        var reference = new StructureRef(new UUID(1, 2), Identifier.withDefaultNamespace("overworld"),
                Identifier.fromNamespaceAndPath("academy", "native_level"), 3);
        return new NativeStructureMiningProgressPacket(19, 82, 13, reference, new UUID(3, 4),
                new BlockPos(-12, 89, 7), 82, stage, status);
    }
}
