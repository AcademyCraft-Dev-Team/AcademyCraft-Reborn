package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.VortexAttackPattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlackWingAttackPacketTest {
    @Test
    void serverPatternTimeAndWorldTargetSurviveNetworkRoundTrip() {
        var pattern = VortexAttackPattern.RISE_SLAM;
        for (int i = 0; i < 6; i++) {
            assertEquals(i % 3 + 1, pattern.id());
            var packet = new BlackWingAttackPacket(345, pattern, 123456L,
                    new Vec3(-23.25, 87.5, 15.75));
            var buffer = Unpooled.buffer();
            try {
                BlackWingAttackPacket.CODEC.encode(buffer, packet);
                var decoded = BlackWingAttackPacket.CODEC.decode(buffer);
                assertEquals(packet.entityId(), decoded.entityId());
                assertEquals(pattern, decoded.pattern());
                assertEquals(packet.startTick(), decoded.startTick());
                assertEquals(packet.target(), decoded.target());
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
            pattern = pattern.next();
        }
    }
}
