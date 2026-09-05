package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.VortexAttackPattern;
import org.junit.jupiter.api.Test;
import org.academy.api.common.ability.VortexAttackTargets;
import org.academy.api.common.ability.VortexAttackSequence;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlackWingAttackPacketTest {
    @Test
    void serverPatternTimeAndWorldTargetSurviveNetworkRoundTrip() {
        var pattern = VortexAttackPattern.RISE_SLAM;
        int[] order = {1, 4, 5, 2, 3};
        for (int i = 0; i < 10; i++) {
            assertEquals(order[i % order.length], pattern.id());
            assertEquals(30, pattern.durationTicks());
            var targets = pattern == VortexAttackPattern.FOURFOLD_SLAM
                    ? List.of(new Vec3(-23.25, 87.5, 15.75), new Vec3(-23.25, 77.5, -15.75),
                            new Vec3(23.25, 80.5, 15.75), new Vec3(23.25, 81.5, -15.75))
                    : List.of(new Vec3(-23.25, 87.5, 15.75));
            var packet = new BlackWingAttackPacket(345, pattern, 123456L, targets);
            var buffer = Unpooled.buffer();
            try {
                BlackWingAttackPacket.CODEC.encode(buffer, packet);
                var decoded = BlackWingAttackPacket.CODEC.decode(buffer);
                assertEquals(packet.entityId(), decoded.entityId());
                assertEquals(pattern, decoded.pattern());
                assertEquals(packet.startTick(), decoded.startTick());
                assertEquals(packet.targets(), decoded.targets());
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
            pattern = pattern.next();
        }
    }

    @Test
    void quadrilateralSurroundsTheCasterAtEveryHeading() {
        var center = new Vec3(103, 72, -245);
        for (int yaw = 0; yaw < 360; yaw += 30) {
            var heading = Vec3.directionFromRotation(0f, yaw);
            var corners = VortexAttackTargets.quadrilateral(center, heading, 6, 6);
            var average = corners.stream().reduce(Vec3.ZERO, Vec3::add).scale(0.25);
            assertEquals(0, average.distanceTo(center), 1.0E-8);
            for (int i = 0; i < 4; i++) {
                var offset = corners.get(i).subtract(center);
                assertEquals(72, offset.lengthSqr(), 1.0E-5);
                assertEquals(i % 2 == 0 ? 6 : -6, offset.dot(heading), 1.0E-5);
                assertEquals(0, offset.y, 1.0E-8);
            }
        }
    }

    @Test
    void rapidClicksCannotInterruptTheAnimationOrChargeExtraCp() {
        var sequence = new VortexAttackSequence();
        var charged = new AtomicInteger();
        assertEquals(VortexAttackPattern.RISE_SLAM, sequence.tryBegin(100, () -> {
            charged.incrementAndGet();
            return true;
        }));
        for (int tick = 101; tick < 130; tick++) {
            assertNull(sequence.tryBegin(tick, () -> {
                charged.incrementAndGet();
                return true;
            }));
        }
        assertEquals(1, charged.get());
        assertNull(sequence.tryBegin(130, () -> false));
        assertEquals(VortexAttackPattern.LEFT_WHIP, sequence.tryBegin(131, () -> true));
        assertNull(sequence.tryBegin(160, () -> true));
        assertEquals(VortexAttackPattern.RIGHT_WHIP, sequence.tryBegin(161, () -> true));
    }
}
