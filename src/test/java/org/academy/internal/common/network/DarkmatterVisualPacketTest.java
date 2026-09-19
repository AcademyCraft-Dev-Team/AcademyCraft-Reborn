package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.vfx.RepairVisualParts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterVisualPacketTest {
    @Test void authoritativeSectorsAndPitchedDirectionSurviveTheWire() {
        var origin = new Vec3(-500.5, 81.625, 300.25);
        var area = new org.academy.api.common.vfx.DirectionalArea(origin, new Vec3(0, 0.6, 0.8),
                new org.academy.api.common.vfx.DirectionalArea.Cone(18.4, Math.cos(Math.toRadians(23))),
                new org.academy.api.common.vfx.DirectionalArea.Cone(13.8, Math.cos(Math.toRadians(64))));
        var packet = new DarkmatterVisualPacket(Identifier.withDefaultNamespace("overworld"),
                DarkmatterVisualPacket.Kind.INTERFERENCE, 42, true, origin, 0.6f, 1.8f, 18.4f, 0, false, 1, area);
        var buffer = Unpooled.buffer();
        try {
            DarkmatterVisualPacket.CODEC.encode(buffer, packet);
            var copy = DarkmatterVisualPacket.CODEC.decode(buffer);
            assertEquals(area, copy.area);
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void everyKindAndTerminalStateSurvivesTheWire() {
        for (var kind : DarkmatterVisualPacket.Kind.values()) for (boolean active : new boolean[]{true, false}) {
            var packet = new DarkmatterVisualPacket(Identifier.withDefaultNamespace("overworld"), kind, 42, active,
                    new Vec3(-12, 81.5, 600), 0.6f, 1.8f, 18, RepairVisualParts.MAIN_HAND | RepairVisualParts.BODY, true, 42);
            var buffer = Unpooled.buffer();
            try {
                DarkmatterVisualPacket.CODEC.encode(buffer, packet);
                assertTrue(buffer.readableBytes() < 100);
                var copy = DarkmatterVisualPacket.CODEC.decode(buffer);
                assertEquals(0, buffer.readableBytes());
                assertEquals(packet.dimension, copy.dimension);
                assertEquals(kind, copy.kind); assertEquals(active, copy.active);
                assertEquals(packet.position, copy.position); assertEquals(packet.parts, copy.parts);
                assertEquals(packet.entityId, copy.entityId); assertEquals(packet.seed, copy.seed);
                assertEquals(packet.range, copy.range); assertEquals(packet.width, copy.width); assertEquals(packet.height, copy.height);
                assertTrue(copy.finished);
            } finally { buffer.release(); }
        }
    }

    @Test void rejectsUnboundedOrNonFiniteVisualInputs() {
        var dimension = Identifier.withDefaultNamespace("overworld");
        for (float width : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1, 100}) {
            assertThrows(IllegalArgumentException.class, () -> new DarkmatterVisualPacket(dimension,
                    DarkmatterVisualPacket.Kind.REPAIR, 0, true, Vec3.ZERO, width, 2, 16, 1, false, 42));
        }
        assertThrows(IllegalArgumentException.class, () -> new DarkmatterVisualPacket(dimension,
                DarkmatterVisualPacket.Kind.INTERFERENCE, 0, true, new Vec3(Double.NaN, 0, 0), 1, 1, 16, 0, false, 42));
    }
}
