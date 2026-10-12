package org.academy.internal.common.structure.space;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeStructureLidTargetsTest {
    @Test
    void positionsAreDefensivelyCopiedAndNeverExposeMutableKeys() {
        var mutable = new BlockPos.MutableBlockPos(-32, -64, 31);
        var positions = new HashSet<BlockPos>();
        positions.add(mutable);
        var targets = new NativeStructureLidTargets(positions);
        mutable.set(0, 0, 0);
        positions.clear();
        assertTrue(targets.isOpen(new BlockPos(-32, -64, 31)));
        assertFalse(targets.isOpen(BlockPos.ZERO));
        assertThrows(UnsupportedOperationException.class, () -> targets.openPositions().clear());
    }

    @Test
    void emptyTargetsUseOneByteAndCanonicalEmptyInstance() {
        var buffer = buffer();
        try {
            NativeStructureLidTargets.STREAM_CODEC.encode(buffer, NativeStructureLidTargets.EMPTY);
            assertEquals(1, buffer.readableBytes());
            assertSame(NativeStructureLidTargets.EMPTY, NativeStructureLidTargets.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void maximumPayloadIsOnlyBoundedCellCoordinates() {
        var positions = new HashSet<BlockPos>();
        for (var x = -32; x < 32; x++) {
            for (var z = -32; z < 32; z++) positions.add(new BlockPos(x, 319, z));
        }
        assertEquals(NativeStructureLidTargets.MAX_OPEN_CHESTS, positions.size());
        var expected = new NativeStructureLidTargets(positions);
        var buffer = buffer();
        try {
            NativeStructureLidTargets.STREAM_CODEC.encode(buffer, expected);
            assertEquals(2 + 4 * positions.size(), buffer.readableBytes());
            assertEquals(expected, NativeStructureLidTargets.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
        positions.add(new BlockPos(0, 318, 0));
        assertThrows(IllegalArgumentException.class, () -> new NativeStructureLidTargets(positions));
    }

    @Test
    void encodingIsDeterministicRegardlessOfSourceOrder() {
        var first = new LinkedHashSet<BlockPos>();
        first.add(new BlockPos(31, 319, 31));
        first.add(new BlockPos(-32, -64, -32));
        first.add(BlockPos.ZERO);
        var second = new LinkedHashSet<BlockPos>();
        second.add(BlockPos.ZERO);
        second.add(new BlockPos(-32, -64, -32));
        second.add(new BlockPos(31, 319, 31));
        assertArrayEquals(encode(new NativeStructureLidTargets(first)), encode(new NativeStructureLidTargets(second)));
    }

    @Test
    void hostileCountsFailBeforeReadingAnyPositions() {
        for (var count : new int[]{-1, NativeStructureLidTargets.MAX_OPEN_CHESTS + 1, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(count);
                buffer.writeByte(97);
                assertThrows(DecoderException.class, () -> NativeStructureLidTargets.STREAM_CODEC.decode(buffer));
                assertEquals(1, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void outOfDomainAndDuplicateCoordinatesAreRejected() {
        for (var position : new BlockPos[]{new BlockPos(-33, 0, 0), new BlockPos(32, 0, 0),
                new BlockPos(0, -65, 0), new BlockPos(0, 320, 0),
                new BlockPos(0, 0, -33), new BlockPos(0, 0, 32)}) {
            assertThrows(IllegalArgumentException.class, () -> new NativeStructureLidTargets(Set.of(position)));
            var buffer = buffer();
            try {
                buffer.writeVarInt(1);
                writePosition(buffer, position);
                assertThrows(DecoderException.class, () -> NativeStructureLidTargets.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
        var buffer = buffer();
        try {
            buffer.writeVarInt(2);
            writePosition(buffer, BlockPos.ZERO);
            writePosition(buffer, BlockPos.ZERO);
            assertThrows(DecoderException.class, () -> NativeStructureLidTargets.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void truncatedCoordinatePayloadIsNeverAccepted() {
        var buffer = buffer();
        try {
            buffer.writeVarInt(1);
            buffer.writeByte(0);
            buffer.writeShort(80);
            assertThrows(IndexOutOfBoundsException.class, () -> NativeStructureLidTargets.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static byte[] encode(NativeStructureLidTargets targets) {
        var buffer = buffer();
        try {
            NativeStructureLidTargets.STREAM_CODEC.encode(buffer, targets);
            var encoded = new byte[buffer.readableBytes()];
            buffer.readBytes(encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    private static void writePosition(RegistryFriendlyByteBuf buffer, BlockPos position) {
        buffer.writeByte(position.getX());
        buffer.writeShort(position.getY());
        buffer.writeByte(position.getZ());
    }
}
