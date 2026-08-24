package org.academy.internal.common.ability.mentalout;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MentalResistanceManagerTest {
    @Test
    void breakThresholdUsesTwiceTheSquaredMentaloutLevelPlusFive() {
        assertEquals(7, MentalResistanceManager.breakThreshold(1));
        assertEquals(13, MentalResistanceManager.breakThreshold(2));
        assertEquals(23, MentalResistanceManager.breakThreshold(3));
        assertEquals(37, MentalResistanceManager.breakThreshold(4));
        assertEquals(55, MentalResistanceManager.breakThreshold(5));
    }

    @Test
    void resistanceDurationFallsByTwoSecondsPerMentaloutLevel() {
        assertEquals(360, MentalResistanceManager.resistanceTicks(1));
        assertEquals(320, MentalResistanceManager.resistanceTicks(2));
        assertEquals(280, MentalResistanceManager.resistanceTicks(3));
        assertEquals(240, MentalResistanceManager.resistanceTicks(4));
        assertEquals(200, MentalResistanceManager.resistanceTicks(5));
    }

    @Test
    void onlyWasdAndMouseEdgesCountAndTakeoverDoublesThem() {
        assertEquals(0, MentalResistanceManager.inputPoints(0, false));
        assertEquals(1, MentalResistanceManager.inputPoints(1, false));
        assertEquals(6, MentalResistanceManager.inputPoints(0x3F, false));
        assertEquals(12, MentalResistanceManager.inputPoints(0xFF, true));
    }

    @Test
    void inputPacketMasksUnsupportedBitsDuringRoundTrip() {
        var buffer = Unpooled.buffer();
        try {
            var packet = new MentalResistanceManager.InputPacket(42L, 0xFF);
            MentalResistanceManager.InputPacket.CODEC.encode(buffer, packet);
            var decoded = MentalResistanceManager.InputPacket.CODEC.decode(buffer);
            assertEquals(42L, decoded.sequence());
            assertEquals(MentalResistanceManager.INPUT_MASK, decoded.edgeMask());
        } finally {
            buffer.release();
        }
    }

    @Test
    void statePacketPreservesProgressAndTakeoverMode() {
        var buffer = Unpooled.buffer();
        try {
            var packet = new MentalResistanceManager.StatePacket(true, 17, 55, 5, true);
            MentalResistanceManager.StatePacket.CODEC.encode(buffer, packet);
            var decoded = MentalResistanceManager.StatePacket.CODEC.decode(buffer);
            assertTrue(decoded.active());
            assertEquals(17, decoded.points());
            assertEquals(55, decoded.threshold());
            assertEquals(5, decoded.controllerLevel());
            assertTrue(decoded.takeover());
        } finally {
            buffer.release();
        }
    }
}
