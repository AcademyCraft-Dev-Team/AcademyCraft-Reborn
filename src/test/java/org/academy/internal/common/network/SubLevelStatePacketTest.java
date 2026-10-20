package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.sublevel.PlotPos;
import org.academy.internal.common.sublevel.SubLevelId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelStatePacketTest {
    @Test
    void dimensionSurvivesSnapshotAndRemovalRoundTrips() {
        var dimension = net.minecraft.resources.Identifier.parse("minecraft:the_nether");
        for (boolean removed : List.of(false, true)) {
            var packet = new SubLevelStatePacket(dimension, SubLevelId.random(), new PlotPos(0, 0),
                    StructurePose.identity(Vec3.ZERO), List.of(),
                    org.academy.internal.common.structure.space.NativeStructureView.EMPTY, removed, true);
            var buffer = Unpooled.buffer();
            try {
                SubLevelStatePacket.CODEC.encode(buffer, packet);
                var decoded = SubLevelStatePacket.CODEC.decode(buffer);
                assertEquals(dimension, decoded.dimension());
                assertEquals(removed, decoded.removed());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void maximumCollisionPayloadRoundTripsAndRejectsOverflow() {
        var boxes = java.util.stream.IntStream.range(0, 16_384)
                .mapToObj(index -> new AABB(index % 128, 0, index / 128,
                        index % 128 + 0.5, 1, index / 128 + 0.5)).toList();
        var packet = new SubLevelStatePacket(SubLevelId.random(), new PlotPos(0, 0),
                StructurePose.identity(Vec3.ZERO), boxes,
                org.academy.internal.common.structure.space.NativeStructureView.EMPTY, false, false);
        var buffer = Unpooled.buffer();
        try {
            SubLevelStatePacket.CODEC.encode(buffer, packet);
            assertTrue(buffer.readableBytes() < 1_048_576);
            assertEquals(boxes, SubLevelStatePacket.CODEC.decode(buffer).boxes());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
        assertThrows(IllegalArgumentException.class, () -> new SubLevelStatePacket(packet.id(), packet.plot(),
                packet.pose(), java.util.Collections.nCopies(16_385, boxes.getFirst()), packet.view(), false));
    }

    @Test
    void snapshotRoundTripsWithoutLosingPoseOrBoxes() {
        var id = SubLevelId.random();
        var pose = StructurePose.identity(new Vec3(12.5, 64, -3.25));
        var view = org.academy.internal.common.structure.space.NativeStructureView.EMPTY;
        var packet = new SubLevelStatePacket(id, new PlotPos(4, 7), pose,
                List.of(new AABB(0, 0, 0, 1, 1, 1), new AABB(1, 0, 0, 2, 1, 1)), view, false);
        var buffer = Unpooled.buffer();
        SubLevelStatePacket.CODEC.encode(buffer, packet);
        var decoded = SubLevelStatePacket.CODEC.decode(buffer);
        assertEquals(id, decoded.id());
        assertEquals(new PlotPos(4, 7), decoded.plot());
        assertEquals(pose, decoded.pose());
        assertEquals(packet.boxes(), decoded.boxes());
        assertEquals(view.cells(), decoded.view().cells());
        assertFalse(decoded.removed());
        assertFalse(buffer.isReadable());
    }

    @Test
    void deltaPacketKeepsFullViewFlag() {
        var packet = new SubLevelStatePacket(SubLevelId.random(), new PlotPos(0, 0),
                StructurePose.identity(Vec3.ZERO), List.of(),
                org.academy.internal.common.structure.space.NativeStructureView.EMPTY, false, false);
        var buffer = Unpooled.buffer();
        SubLevelStatePacket.CODEC.encode(buffer, packet);
        assertFalse(SubLevelStatePacket.CODEC.decode(buffer).fullView());
    }

    @Test
    void removalPacketIsRepresentable() {
        var packet = new SubLevelStatePacket(SubLevelId.random(), new PlotPos(0, 0),
                StructurePose.identity(Vec3.ZERO), List.of(),
                org.academy.internal.common.structure.space.NativeStructureView.EMPTY, true);
        var buffer = Unpooled.buffer();
        SubLevelStatePacket.CODEC.encode(buffer, packet);
        assertTrue(SubLevelStatePacket.CODEC.decode(buffer).removed());
    }
}
