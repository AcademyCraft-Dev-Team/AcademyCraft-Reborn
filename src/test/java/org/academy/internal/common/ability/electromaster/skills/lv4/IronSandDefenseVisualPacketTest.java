package org.academy.internal.common.ability.electromaster.skills.lv4;

import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IronSandDefenseVisualPacketTest {
    @Test void directionsAndDistantWorldImpactCoordinatesRoundTrip() {
        for (boolean intercept : new boolean[]{false, true}) {
            var buffer = Unpooled.buffer();
            try {
                var vector = intercept ? new Vec3(29999982.125, 65.4, -29999981.75) : new Vec3(-0.5, 0.4, 0.8);
                IronSandArsenal.DefenseVisualPacket.CODEC.encode(buffer, new IronSandArsenal.DefenseVisualPacket(321, intercept, vector));
                var packet = IronSandArsenal.DefenseVisualPacket.CODEC.decode(buffer);
                assertEquals(321, packet.entityId);
                assertEquals(intercept, packet.intercept);
                assertEquals(vector, packet.vector);
            } finally { buffer.release(); }
        }
    }
}
