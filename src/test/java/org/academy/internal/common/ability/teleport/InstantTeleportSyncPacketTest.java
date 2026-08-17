package org.academy.internal.common.ability.teleport;

import net.minecraft.world.entity.Relative;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstantTeleportSyncPacketTest {
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
