package org.academy.internal.common.structure.space;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** State-bearing model checks run in GameTest; these wire guards need no vanilla bootstrap. */
class NativeStructureViewTest {
    private static final UUID STRUCTURE_ID = UUID.fromString("2248566d-8878-4f65-a955-a7e59b66b46e");
    private static final UUID RUNTIME_ID = UUID.fromString("3d59d251-7fa2-470d-b676-54be59a015a9");

    @Test
    void emptyViewHasOneByteWireRepresentationAndAnImmutableCellList() {
        var buffer = buffer();
        try {
            NativeStructureView.STREAM_CODEC.encode(buffer, NativeStructureView.EMPTY);
            assertEquals(1, buffer.readableBytes());
            assertSame(NativeStructureView.EMPTY, NativeStructureView.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
            assertThrows(UnsupportedOperationException.class, () -> NativeStructureView.EMPTY.cells().add(null));
        } finally {
            buffer.release();
        }
    }

    @Test
    void hostileCellCountsFailBeforeAllocatingOrReadingAnyCells() {
        for (var size : new int[]{-1, NativeStructureView.MAX_CELLS + 1, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(size);
                assertThrows(DecoderException.class, () -> NativeStructureView.STREAM_CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new NativeStructureView(
                Collections.nCopies(NativeStructureView.MAX_CELLS + 1, null)));
    }

    @Test
    void extendedCoordinateHeaderStillRejectsUnboundedCounts() {
        for (var size : new int[]{-1, NativeStructureView.MAX_CELLS + 1, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(65_536); buffer.writeVarInt(size); buffer.writeByte(97);
                assertThrows(DecoderException.class, () -> NativeStructureView.STREAM_CODEC.decode(buffer));
                assertEquals(1, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test
    void smallHeaderPreservesPersistentAndRuntimeIdentitiesAndArbitraryPose() {
        var reference = new StructureRef(STRUCTURE_ID, Identifier.withDefaultNamespace("overworld"),
                Identifier.fromNamespaceAndPath("academy", "native_level"), 7);
        var pose = new StructurePose(new Vec3(-29_000_000.25, -63.75, 29_000_000.5),
                new Quaterniond().rotationXYZ(0.731, -1.257, 2.311));
        var expected = new NativeStructureViewState(reference, RUNTIME_ID, 1_234, pose);
        var buffer = buffer();
        try {
            NativeStructureViewState.STREAM_CODEC.encode(buffer, expected);
            assertTrue(buffer.readableBytes() < 256, "Moving a view sends only a small header");
            var actual = NativeStructureViewState.STREAM_CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            assertEquals(reference, actual.reference());
            assertEquals(RUNTIME_ID, actual.runtimeId());
            assertEquals(expected.revision(), actual.revision());
            assertTrue(NativeStructureQueries.equivalentPose(pose, actual.pose()));
        } finally {
            buffer.release();
        }
    }

    @Test
    void headerDecoderRejectsNegativeVersionsAndInvalidTransforms() {
        for (var invalid : new double[][]{
                {-1, 1, 0, 0, 1}, {0, -1, 0, 0, 1},
                {0, 1, Double.NaN, 0, 1}, {0, 1, Double.POSITIVE_INFINITY, 0, 1},
                {0, 1, 0, Double.NaN, 1}, {0, 1, 0, 0, 0}
        }) {
            var buffer = buffer();
            try {
                writeHeader(buffer, (long) invalid[0], (long) invalid[1], invalid[2], invalid[3], invalid[4]);
                assertThrows(DecoderException.class, () -> NativeStructureViewState.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    private static void writeHeader(RegistryFriendlyByteBuf buffer, long generation, long revision,
                                    double originX, double rotationX, double rotationW) {
        buffer.writeUUID(STRUCTURE_ID);
        buffer.writeIdentifier(Identifier.withDefaultNamespace("overworld"));
        buffer.writeIdentifier(Identifier.fromNamespaceAndPath("academy", "native_level"));
        buffer.writeVarLong(generation);
        buffer.writeUUID(RUNTIME_ID);
        buffer.writeVarLong(revision);
        buffer.writeDouble(originX);
        buffer.writeDouble(0);
        buffer.writeDouble(0);
        buffer.writeDouble(rotationX);
        buffer.writeDouble(0);
        buffer.writeDouble(0);
        buffer.writeDouble(rotationW);
    }
}
