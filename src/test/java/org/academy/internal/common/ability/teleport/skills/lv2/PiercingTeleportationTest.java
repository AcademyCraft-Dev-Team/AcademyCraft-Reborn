package org.academy.internal.common.ability.teleport.skills.lv2;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiercingTeleportationTest {
    @Test
    void firstScrollStartsFromDisplayedDefaultDestination() {
        assertEquals(7.0, PiercingTeleportation.resolveScrolledDistance(
                40.0, 6.0, true, 1.0));
        assertEquals(5.0, PiercingTeleportation.resolveScrolledDistance(
                40.0, 6.0, true, -1.0));
        assertEquals(41.0, PiercingTeleportation.resolveScrolledDistance(
                40.0, Double.NaN, true, 1.0));
    }

    @Test
    void scrollDistanceRemainsWithinSkillRange() {
        assertEquals(0.0, PiercingTeleportation.resolveScrolledDistance(
                0.0, Double.NaN, false, -1.0));
        assertEquals(64.0, PiercingTeleportation.resolveScrolledDistance(
                64.0, Double.NaN, false, 1.0));
    }

    @Test
    void teleportPacketPreservesAutomaticAndManualTargetModes() {
        var automatic = roundTrip(new PiercingTeleportation.TeleportPacket(40.0, true));
        assertEquals(40.0, automatic.getDistance());
        assertTrue(automatic.useDefaultTarget());

        var manual = roundTrip(new PiercingTeleportation.TeleportPacket(12.5, false));
        assertEquals(12.5, manual.getDistance());
        assertFalse(manual.useDefaultTarget());
    }

    private static PiercingTeleportation.TeleportPacket roundTrip(
            PiercingTeleportation.TeleportPacket packet
    ) {
        var buffer = Unpooled.buffer();
        try {
            PiercingTeleportation.TeleportPacket.CODEC.encode(buffer, packet);
            return PiercingTeleportation.TeleportPacket.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
