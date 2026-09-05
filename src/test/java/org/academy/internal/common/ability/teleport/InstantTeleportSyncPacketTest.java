package org.academy.internal.common.ability.teleport;

import net.minecraft.world.entity.Relative;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstantTeleportSyncPacketTest {
    @Test
    void explicitRotationSurvivesPacketRoundTrip() {
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            var packet = new InstantTeleportSyncPacket(7, net.minecraft.world.phys.Vec3.ZERO,
                    -90.0f, 30.0f, false);
            InstantTeleportSyncPacket.CODEC.encode(buffer, packet);
            var decoded = InstantTeleportSyncPacket.CODEC.decode(buffer);
            org.junit.jupiter.api.Assertions.assertFalse(decoded.preserveViewRotation());
            assertEquals(-90.0f, InstantTeleportSyncPacket.resolveRotation(
                    decoded.preserveViewRotation(), 73.0f, decoded.yRot()));
            assertEquals(30.0f, decoded.xRot());
        } finally {
            buffer.release();
        }
    }

    @Test
    void localPlayerKeepsClientViewRotation() {
        assertEquals(73.0f, InstantTeleportSyncPacket.resolveRotation(
                true, 73.0f, -25.0f));
    }

    @Test
    void observersUseSynchronizedEntityRotation() {
        assertEquals(-25.0f, InstantTeleportSyncPacket.resolveRotation(
                false, 73.0f, -25.0f));
    }

    @Test
    void vanillaTeleportPreservesClientViewWithZeroRelativeRotation() {
        assertEquals(
                Set.of(Relative.Y_ROT, Relative.X_ROT),
                TeleportSync.PRESERVED_VIEW_ROTATION
        );
    }
}
