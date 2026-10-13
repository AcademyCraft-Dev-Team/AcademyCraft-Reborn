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
    void everyActiveIntentCarriesAFreshObservationWithoutClientTiming() {
        for (var action : NativeStructureMinePacket.Action.values()) {
            var packet = new NativeStructureMinePacket(action, 91, action == NativeStructureMinePacket.Action.ABORT ? null : hit());
            var buffer = Unpooled.buffer();
            try {
                NativeStructureMinePacket.CODEC.encode(buffer, packet);
                assertTrue(buffer.readableBytes() < 512);
                if (action == NativeStructureMinePacket.Action.ABORT) assertEquals(9, buffer.readableBytes());
                var copy = NativeStructureMinePacket.CODEC.decode(buffer);
                assertEquals(action, copy.action());
                assertEquals(91, copy.requestId());
                if (action != NativeStructureMinePacket.Action.START) assertNull(copy.startHit());
                if (packet.observation() == null) assertNull(copy.observation());
                else {
                    assertEquals(packet.observation().block(), copy.observation().block());
                    assertEquals(packet.observation().runtimeId(), copy.observation().runtimeId());
                    assertEquals(packet.observation().geometryRevision(), copy.observation().geometryRevision());
                    assertEquals(packet.observation().localPoint(), copy.observation().localPoint());
                    assertEquals(packet.observation().worldPoint(), copy.observation().worldPoint());
                    assertTrue(copy.observation().pose().localToWorldPoint(copy.observation().localPoint())
                            .distanceTo(copy.observation().worldPoint()) < 1.0e-10);
                }
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test
    void miningHasNoHandFieldAndRejectsAnyAppendedHand() {
        for (int hand : new int[]{1, 2, 255}) {
            var buffer = Unpooled.buffer();
            try {
                NativeStructureMinePacket.CODEC.encode(buffer, new NativeStructureMinePacket(NativeStructureMinePacket.Action.START, 1, hit()));
                buffer.writeByte(hand);
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
                buffer.writeByte(NativeStructureMinePacket.Action.ABORT.ordinal()).writeLong(id);
                assertThrows(IllegalArgumentException.class, () -> NativeStructureMinePacket.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
        var buffer = Unpooled.buffer();
        try {
            NativeStructureMinePacket.CODEC.encode(buffer, new NativeStructureMinePacket(NativeStructureMinePacket.Action.HOLD, 1, hit()));
            buffer.writeLong(1_000_000); // A forged "elapsed ticks" value is not part of the contract.
            assertThrows(IllegalArgumentException.class, () -> NativeStructureMinePacket.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test
    void allActiveActionsNeedObservationsWhileAbortForbidsOne() {
        for (var action : new NativeStructureMinePacket.Action[]{NativeStructureMinePacket.Action.START,
                NativeStructureMinePacket.Action.HOLD, NativeStructureMinePacket.Action.STOP}) {
            assertThrows(IllegalArgumentException.class, () -> new NativeStructureMinePacket(action, 1, null));
            var buffer = Unpooled.buffer();
            try {
                buffer.writeByte(action.ordinal()).writeLong(1);
                assertThrows(IndexOutOfBoundsException.class, () -> NativeStructureMinePacket.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
        assertThrows(IllegalArgumentException.class, () -> new NativeStructureMinePacket(NativeStructureMinePacket.Action.ABORT, 1, hit()));
        var buffer = Unpooled.buffer();
        try {
            buffer.writeByte(NativeStructureMinePacket.Action.ABORT.ordinal()).writeLong(1);
            NativeStructureHitCodec.CODEC.encode(buffer, hit());
            assertThrows(IllegalArgumentException.class, () -> NativeStructureMinePacket.CODEC.decode(buffer));
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
