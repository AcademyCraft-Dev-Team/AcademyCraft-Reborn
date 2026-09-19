package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MagneticSupportPacketTest {
    @Test void supportFacesAndStopSurviveTheWire() {
        var dimension = Identifier.withDefaultNamespace("overworld");
        for (boolean active : new boolean[]{true, false}) for (var face : Direction.values()) {
            var point = new Vec3(10.5, 81.5, -25.25);
            var buffer = Unpooled.buffer();
            try {
                MagneticSupportPacket.CODEC.encode(buffer, new MagneticSupportPacket(dimension, 42, active, point, face));
                assertTrue(buffer.readableBytes() < 64);
                var copy = MagneticSupportPacket.CODEC.decode(buffer);
                assertEquals(0, buffer.readableBytes());
                assertEquals(active, copy.active); assertEquals(dimension, copy.dimension); assertEquals(42, copy.entityId);
                if (active) { assertEquals(point, copy.point); assertEquals(face, copy.face); }
            } finally { buffer.release(); }
        }
    }
}
