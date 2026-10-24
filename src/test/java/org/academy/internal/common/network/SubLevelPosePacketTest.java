package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.sublevel.SubLevelId;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelPosePacketTest {
    @Test
    void compactPoseDeltaRetainsDimensionAndRotationWithoutViewPayload() {
        var packet = new SubLevelPosePacket(Identifier.parse("minecraft:the_nether"), SubLevelId.random(),
                new StructurePose(new Vec3(125.25, 80.5, -24), new Quaterniond().rotateXYZ(0.2, 1.5, -0.4)));
        var bytes = Unpooled.buffer();
        try {
            SubLevelPosePacket.CODEC.encode(bytes, packet);
            assertTrue(bytes.readableBytes() < 128);
            var decoded = SubLevelPosePacket.CODEC.decode(bytes);
            assertEquals(packet.dimension(), decoded.dimension());
            assertEquals(packet.id(), decoded.id());
            assertEquals(packet.pose().worldOrigin(), decoded.pose().worldOrigin());
            assertEquals(packet.pose().rotationX(), decoded.pose().rotationX(), 1.0E-12);
            assertEquals(packet.pose().rotationY(), decoded.pose().rotationY(), 1.0E-12);
            assertEquals(packet.pose().rotationZ(), decoded.pose().rotationZ(), 1.0E-12);
            assertEquals(packet.pose().rotationW(), decoded.pose().rotationW(), 1.0E-12);
            assertFalse(bytes.isReadable());
        } finally {
            bytes.release();
        }
    }
}
