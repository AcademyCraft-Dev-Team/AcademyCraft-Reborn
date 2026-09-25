package org.academy.internal.common.ability.electromaster;

import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyStrikeVisualPacketTest {
    @Test
    void codecRoundTripsImpactSeedAndProfile() {
        var expected = new SkyStrikeVisualPacket(
                new Vec3(12.25, 80.0, -41.75),
                0x1234_5678_9abcL,
                SkyStrikeProfile.THUNDERCLAP
        );
        var buffer = Unpooled.buffer();

        SkyStrikeVisualPacket.CODEC.encode(buffer, expected);
        var decoded = SkyStrikeVisualPacket.CODEC.decode(buffer);

        assertEquals(expected.impact(), decoded.impact());
        assertEquals(expected.seed(), decoded.seed());
        assertEquals(expected.profile(), decoded.profile());
    }

    @Test
    void unknownProfileFallsBackToLightweightStorm() {
        var buffer = Unpooled.buffer();
        Vec3.STREAM_CODEC.encode(buffer, Vec3.ZERO);
        ByteBufCodecs.LONG.encode(buffer, 9L);
        buffer.writeByte(127);

        var decoded = SkyStrikeVisualPacket.CODEC.decode(buffer);

        assertEquals(SkyStrikeProfile.LIGHTNING_STORM, decoded.profile());
    }
}
