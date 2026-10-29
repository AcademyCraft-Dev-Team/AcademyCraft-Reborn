package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.sublevel.SubLevelId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelPoseBatchPacketTest {
    private static final Identifier DIMENSION = Identifier.parse("minecraft:overworld");

    @Test void fullBatchPreservesEveryBodyAndUsesLessPayloadThanIndividualMessages() {
        var entries = new ArrayList<SubLevelPoseBatchPacket.Entry>();
        int individualBytes = 0;
        for (int i = 0; i < SubLevelPoseBatchPacket.MAX_ENTRIES; i++) {
            var entry = new SubLevelPoseBatchPacket.Entry(SubLevelId.random(),
                    StructurePose.identity(new Vec3(i * 32.125, 80, -i)), i % 2 == 0);
            entries.add(entry);
            var bytes = Unpooled.buffer();
            try {
                SubLevelPosePacket.CODEC.encode(bytes, new SubLevelPosePacket(DIMENSION, entry.id(), entry.pose()));
                individualBytes += bytes.readableBytes();
            } finally { bytes.release(); }
        }
        var packet = new SubLevelPoseBatchPacket(DIMENSION, entries);
        var bytes = Unpooled.buffer();
        try {
            SubLevelPoseBatchPacket.CODEC.encode(bytes, packet);
            assertTrue(bytes.readableBytes() < individualBytes * 0.85);
            var decoded = SubLevelPoseBatchPacket.CODEC.decode(bytes);
            assertEquals(DIMENSION, decoded.dimension());
            assertEquals(entries, decoded.entries());
            assertFalse(bytes.isReadable());
        } finally { bytes.release(); }
    }

    @Test void rejectsEmptyOversizedDuplicateAndTrailingPayloads() {
        var entry = new SubLevelPoseBatchPacket.Entry(SubLevelId.random(), StructurePose.identity(Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new SubLevelPoseBatchPacket(DIMENSION, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SubLevelPoseBatchPacket(DIMENSION, List.of(entry, entry)));
        for (int size : new int[] {0, 257, 65535}) {
            var bytes = Unpooled.buffer();
            try {
                Identifier.STREAM_CODEC.encode(bytes, DIMENSION);
                bytes.writeShort(size);
                assertThrows(IllegalArgumentException.class, () -> SubLevelPoseBatchPacket.CODEC.decode(bytes));
            } finally { bytes.release(); }
        }
        var bytes = Unpooled.buffer();
        try {
            SubLevelPoseBatchPacket.CODEC.encode(bytes, new SubLevelPoseBatchPacket(DIMENSION, List.of(entry)));
            bytes.writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> SubLevelPoseBatchPacket.CODEC.decode(bytes));
        } finally { bytes.release(); }
    }
}
