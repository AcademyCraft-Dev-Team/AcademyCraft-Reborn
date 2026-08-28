package org.academy.internal.common.ability.teleport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.teleport.skills.lv5.SpacialExcision;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpacialExcisionPacketTest {
    @Test
    void segmentCodecRetainsSessionCoordinatesOrientationAndSharedEndTick() {
        var session = UUID.randomUUID();
        var packet = new SpacialExcision.SegmentPacket(
                session,
                120L,
                180L,
                720L,
                Identifier.parse("minecraft:overworld"),
                new Vec3(1.25, 64.5, -3.75),
                new Vec3(15.0, 67.0, 4.5),
                -90.0f,
                0x1234_5678_9ABCDEFL
        );

        ByteBuf buffer = Unpooled.buffer();
        try {
            SpacialExcision.SegmentPacket.CODEC.encode(buffer, packet);
            var decoded = SpacialExcision.SegmentPacket.CODEC.decode(buffer);

            assertEquals(packet.sessionId(), decoded.sessionId());
            assertEquals(packet.startTick(), decoded.startTick());
            assertEquals(packet.createdTick(), decoded.createdTick());
            assertEquals(packet.endTick(), decoded.endTick());
            assertEquals(packet.dimension(), decoded.dimension());
            assertEquals(packet.start(), decoded.start());
            assertEquals(packet.end(), decoded.end());
            assertEquals(packet.preTeleportYaw(), decoded.preTeleportYaw());
            assertEquals(packet.seed(), decoded.seed());
        } finally {
            buffer.release();
        }
    }
}
