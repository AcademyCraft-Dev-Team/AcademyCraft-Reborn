package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeStructureUsePacketTest {
    private static final UUID STRUCTURE_ID = UUID.fromString("2248566d-8878-4f65-a955-a7e59b66b46e");
    private static final UUID RUNTIME_ID = UUID.fromString("3d59d251-7fa2-470d-b676-54be59a015a9");
    private static final BlockPos POSITION = new BlockPos(-12, 65, 7);

    @Test
    void rotatedNegativeCellObservationSurvivesTheWireWithoutLosingRuntimeIdentity() {
        var pose = new StructurePose(new Vec3(123.25, 67.5, -942.125),
                new Quaterniond().rotationXYZ(0.731, -1.257, 2.311));
        var localPoint = new Vec3(-12.0, 65.25, 7.75);
        var reference = new StructureRef(STRUCTURE_ID, Identifier.withDefaultNamespace("overworld"),
                Identifier.fromNamespaceAndPath("academy", "native_level"), 4);
        var hit = new StructureHit(new SpaceBlockRef(reference, POSITION), RUNTIME_ID, 19, pose,
                Direction.WEST, localPoint, pose.localToWorldPoint(localPoint), false);
        var buffer = Unpooled.buffer();
        try {
            NativeStructureUsePacket.CODEC.encode(buffer, new NativeStructureUsePacket(37, hit, InteractionHand.MAIN_HAND));
            assertTrue(buffer.readableBytes() < 512);
            var packet = NativeStructureUsePacket.CODEC.decode(buffer);
            assertEquals(37, packet.requestId());
            assertEquals(InteractionHand.MAIN_HAND, packet.hand());
            var copy = packet.hit();
            assertEquals(0, buffer.readableBytes());
            assertEquals(hit.block(), copy.block());
            assertEquals(hit.runtimeId(), copy.runtimeId());
            assertEquals(hit.geometryRevision(), copy.geometryRevision());
            assertEquals(hit.localFace(), copy.localFace());
            assertEquals(hit.localPoint(), copy.localPoint());
            assertEquals(hit.worldPoint(), copy.worldPoint());
            assertEquals(hit.inside(), copy.inside());
            assertTrue(copy.pose().localToWorldPoint(localPoint).distanceTo(hit.worldPoint()) < 1.0E-10);
            assertTrue(copy.pose().worldToLocalPoint(copy.worldPoint()).distanceTo(localPoint) < 1.0E-10);
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoderRejectsNonFiniteOrUnboundedCoordinatesInEveryCoordinateSpace() {
        for (var coordinate : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, 30_000_001.0}) {
            var invalid = new Vec3(coordinate, 0.0, 0.0);
            assertRejected(buffer -> writeObservation(buffer, 0, 1, POSITION, Direction.UP.ordinal(),
                    invalid, Vec3.ZERO, Vec3.ZERO));
            assertRejected(buffer -> writeObservation(buffer, 0, 1, POSITION, Direction.UP.ordinal(),
                    Vec3.ZERO, invalid, Vec3.ZERO));
            assertRejected(buffer -> writeObservation(buffer, 0, 1, POSITION, Direction.UP.ordinal(),
                    Vec3.ZERO, Vec3.ZERO, invalid));
        }
    }

    @Test
    void decoderRejectsNegativeIdentityVersionsBeforeTheObservationCanBeUsed() {
        assertRejected(buffer -> writeObservation(buffer, -1, 1, POSITION, Direction.UP.ordinal(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO));
        assertRejected(buffer -> writeObservation(buffer, 0, -1, POSITION, Direction.UP.ordinal(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO));
    }

    @Test
    void decoderRejectsForgedFaceAndCellsOutsideTheIndependentContainer() {
        for (var face : new int[]{Direction.values().length, 255}) {
            assertRejected(buffer -> writeObservation(buffer, 0, 1, POSITION, face,
                    Vec3.ZERO, Vec3.ZERO, Vec3.ZERO));
        }
        for (var position : new BlockPos[]{new BlockPos(-33, 0, 0), new BlockPos(32, 0, 0),
                new BlockPos(0, 0, -33), new BlockPos(0, 0, 32),
                new BlockPos(0, -65, 0), new BlockPos(0, 320, 0)}) {
            assertRejected(buffer -> writeObservation(buffer, 0, 1, position, Direction.UP.ordinal(),
                    Vec3.ZERO, Vec3.ZERO, Vec3.ZERO));
        }
    }

    @Test
    void decoderRejectsAnOversizedIdentifierBeforeReadingTheRemainingPayload() {
        for (var oversizedBackend : new boolean[]{false, true}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeLong(1);
                buffer.writeUUID(STRUCTURE_ID);
                if (oversizedBackend) buffer.writeUtf("minecraft:overworld");
                buffer.writeUtf("a".repeat(257));
                assertThrows(DecoderException.class, () -> NativeStructureUsePacket.CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void explicitHandsRoundTripAndOldConstructorKeepsMainHand() {
        for (var hand : InteractionHand.values()) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                writeObservation(buffer, 0, 1, POSITION, Direction.UP.ordinal(), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
                var hit = NativeStructureUsePacket.CODEC.decode(buffer).hit();
                buffer.clear();
                NativeStructureUsePacket.CODEC.encode(buffer, new NativeStructureUsePacket(hit, hand));
                var result = NativeStructureUsePacket.CODEC.decode(buffer);
                assertEquals(hand, result.hand());
                assertEquals(hit, result.hit());
                assertEquals(0, buffer.readableBytes());
                assertEquals(InteractionHand.MAIN_HAND, new NativeStructureUsePacket(hit).hand());
                assertThrows(NullPointerException.class, () -> new NativeStructureUsePacket(hit, null));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void decoderRejectsEveryNonHandByteAndAnOmittedHand() {
        for (var invalid : new int[]{2, 3, 127, 128, 255}) {
            assertRejected(buffer -> {
                writeObservation(buffer, 0, 1, POSITION, Direction.UP.ordinal(), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
                buffer.setByte(buffer.writerIndex() - 1, invalid);
            });
        }
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writeObservation(buffer, 0, 1, POSITION, Direction.UP.ordinal(), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
            buffer.writerIndex(buffer.writerIndex() - 1);
            assertThrows(IndexOutOfBoundsException.class, () -> NativeStructureUsePacket.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void requestIdentityAndEnvelopeMustBeExact() {
        for (long id : new long[]{0, -1, Long.MIN_VALUE}) {
            assertRejected(buffer -> {
                writeObservation(buffer, 0, 1, POSITION, Direction.UP.ordinal(), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
                buffer.setLong(0, id);
            });
        }
        assertRejected(buffer -> {
            writeObservation(buffer, 0, 1, POSITION, Direction.UP.ordinal(), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
            buffer.writeByte(0);
        });
    }

    private static void assertRejected(Consumer<FriendlyByteBuf> payload) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            payload.accept(buffer);
            assertThrows(IllegalArgumentException.class, () -> NativeStructureUsePacket.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static void writeObservation(FriendlyByteBuf buffer, long generation, long revision,
                                         BlockPos position, int face, Vec3 origin,
                                         Vec3 localPoint, Vec3 worldPoint) {
        buffer.writeLong(1);
        buffer.writeUUID(STRUCTURE_ID);
        buffer.writeUtf("minecraft:overworld");
        buffer.writeUtf("academy:native_level");
        buffer.writeLong(generation);
        buffer.writeUUID(RUNTIME_ID);
        buffer.writeLong(revision);
        writePoint(buffer, origin);
        buffer.writeDouble(0.0);
        buffer.writeDouble(0.0);
        buffer.writeDouble(0.0);
        buffer.writeDouble(1.0);
        buffer.writeBlockPos(position);
        buffer.writeByte(face);
        writePoint(buffer, localPoint);
        writePoint(buffer, worldPoint);
        buffer.writeBoolean(false);
        buffer.writeByte(0);
    }

    private static void writePoint(FriendlyByteBuf buffer, Vec3 point) {
        buffer.writeDouble(point.x);
        buffer.writeDouble(point.y);
        buffer.writeDouble(point.z);
    }
}
