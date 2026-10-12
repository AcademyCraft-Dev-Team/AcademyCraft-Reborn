package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureMinePacketTest {
    @Test
    void actionsRoundTripWithoutClientTimingOrRepeatedHitPayloads() {
        for (var action : NativeStructureMinePacket.Action.values()) {
            var packet = new NativeStructureMinePacket(action, 91, action == NativeStructureMinePacket.Action.START ? hit() : null);
            var buffer = Unpooled.buffer();
            try {
                NativeStructureMinePacket.CODEC.encode(buffer, packet);
                assertTrue(buffer.readableBytes() < 512);
                if (action != NativeStructureMinePacket.Action.START) assertEquals(9, buffer.readableBytes());
                var copy = NativeStructureMinePacket.CODEC.decode(buffer);
                assertEquals(action, copy.action());
                assertEquals(91, copy.requestId());
                if (packet.startHit() == null) assertNull(copy.startHit());
                else {
                    assertEquals(packet.startHit().block(), copy.startHit().block());
                    assertEquals(packet.startHit().runtimeId(), copy.startHit().runtimeId());
                    assertEquals(packet.startHit().geometryRevision(), copy.startHit().geometryRevision());
                    assertEquals(packet.startHit().localPoint(), copy.startHit().localPoint());
                    assertEquals(packet.startHit().worldPoint(), copy.startHit().worldPoint());
                    assertTrue(copy.startHit().pose().localToWorldPoint(copy.startHit().localPoint())
                            .distanceTo(copy.startHit().worldPoint()) < 1.0e-10);
                }
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test
    void startCannotSmuggleAnOffhandOrInvalidHandThroughTheSharedObservationCodec() {
        for (int hand : new int[]{1, 2, 255}) {
            var buffer = Unpooled.buffer();
            try {
                NativeStructureMinePacket.CODEC.encode(buffer, new NativeStructureMinePacket(NativeStructureMinePacket.Action.START, 1, hit()));
                buffer.setByte(buffer.writerIndex() - 1, hand);
                assertThrows(IllegalArgumentException.class, () -> NativeStructureMinePacket.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test
    void invalidActionsIdentitiesAndUnexpectedProgressDataAreRejected() {
        for (int action : new int[]{4, 127, 255}) {
            var buffer = Unpooled.buffer();
            try {
                buffer.writeByte(action).writeLong(1);
                assertThrows(IllegalArgumentException.class, () -> NativeStructureMinePacket.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
        for (long id : new long[]{0, -1, Long.MIN_VALUE}) {
            var buffer = Unpooled.buffer();
            try {
                buffer.writeByte(NativeStructureMinePacket.Action.HOLD.ordinal()).writeLong(id);
                assertThrows(IllegalArgumentException.class, () -> NativeStructureMinePacket.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
        var buffer = Unpooled.buffer();
        try {
            NativeStructureMinePacket.CODEC.encode(buffer, new NativeStructureMinePacket(NativeStructureMinePacket.Action.HOLD, 1, null));
            buffer.writeLong(1_000_000); // A forged "elapsed ticks" value is not part of the contract.
            assertThrows(IllegalArgumentException.class, () -> NativeStructureMinePacket.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test
    void startNeedsAnObservationAndContinuationMustNotContainOne() {
        assertThrows(IllegalArgumentException.class, () -> new NativeStructureMinePacket(NativeStructureMinePacket.Action.START, 1, null));
        for (var action : new NativeStructureMinePacket.Action[]{NativeStructureMinePacket.Action.HOLD,
                NativeStructureMinePacket.Action.STOP, NativeStructureMinePacket.Action.ABORT}) {
            assertThrows(IllegalArgumentException.class, () -> new NativeStructureMinePacket(action, 1, hit()));
        }
        var buffer = Unpooled.buffer();
        try {
            buffer.writeByte(NativeStructureMinePacket.Action.START.ordinal()).writeLong(1);
            assertThrows(IndexOutOfBoundsException.class, () -> NativeStructureMinePacket.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    private static StructureHit hit() {
        var reference = new StructureRef(new UUID(1, 2), Identifier.withDefaultNamespace("overworld"),
                Identifier.fromNamespaceAndPath("academy", "native_level"), 3);
        var pose = new StructurePose(new Vec3(13, 81, -17), new Quaterniond().rotationXYZ(.31, .57, -.22));
        var local = new Vec3(-2.5, 82, 1.5);
        return new StructureHit(new SpaceBlockRef(reference, new BlockPos(-3, 81, 1)), new UUID(3, 4), 12,
                pose, Direction.UP, local, pose.localToWorldPoint(local), false);
    }
}
