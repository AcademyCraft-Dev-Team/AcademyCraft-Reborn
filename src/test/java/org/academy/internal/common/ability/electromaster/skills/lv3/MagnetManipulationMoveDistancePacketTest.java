package org.academy.internal.common.ability.electromaster.skills.lv3;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MagnetManipulationMoveDistancePacketTest {
    @Test
    void wheelOffsetsRoundTripWithoutLosingPrecision() {
        for (var offset : new double[]{1.0, -1.0, 0.25, -0.5, 2.5}) {
            var buffer = Unpooled.buffer();
            try {
                MagnetManipulation.MoveDistancePacket.CODEC.encode(buffer,
                        new MagnetManipulation.MoveDistancePacket(offset));
                assertEquals(offset, MagnetManipulation.MoveDistancePacket.CODEC.decode(buffer).getScrollOffset(), 1.0e-9);
            } finally {
                buffer.release();
            }
        }
    }
}
