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
                assertTrue(buffer.readableBytes() < 128);
                var copy = DarkmatterVisualPacket.CODEC.decode(buffer);
                assertEquals(0, buffer.readableBytes());
                assertEquals(packet.dimension, copy.dimension);
                assertEquals(kind, copy.kind); assertEquals(active, copy.active);
                assertEquals(packet.position, copy.position); assertEquals(packet.parts, copy.parts);
                assertEquals(packet.entityId, copy.entityId); assertEquals(packet.seed, copy.seed);
                assertEquals(packet.range, copy.range); assertEquals(packet.width, copy.width); assertEquals(packet.height, copy.height);
                assertEquals(packet.alpha, copy.alpha); assertEquals(packet.beta, copy.beta); assertEquals(packet.gamma, copy.gamma);
                assertEquals(packet.variant, copy.variant); assertEquals(packet.direction, copy.direction);
                assertTrue(copy.finished);
            } finally { buffer.release(); }
        }
    }

    @Test void independentShapePhaseAndMirroredCutDirectionSurviveTheWire() {
        var dimension = Identifier.withDefaultNamespace("overworld");
        for (int reverse : new int[]{-1, 1}) {
            var packet = new DarkmatterVisualPacket(dimension, DarkmatterVisualPacket.Kind.CUT, -1, true,
                    Vec3.ZERO, 2, .9f, 8.8f, 0, false, 17, null, 4, 1, 2, reverse, new Vec3(.6, 0, .8));
            var buffer = Unpooled.buffer();
            try {
                DarkmatterVisualPacket.CODEC.encode(buffer, packet);
                var copy = DarkmatterVisualPacket.CODEC.decode(buffer);
                assertEquals(4, copy.alpha); assertEquals(1, copy.beta); assertEquals(2, copy.gamma);
                assertEquals(reverse, copy.variant); assertEquals(packet.direction, copy.direction);
                assertEquals(0, buffer.readableBytes());
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

    @Test void disassemblyContactAndCasterPhaseSurviveTheWire() {
        var contact = new DarkmatterVisualPacket.SurfaceContact(new Vec3(.3, 1.2, -.12), new Vec3(2, 0, 0));
        var packet = new DarkmatterVisualPacket(Identifier.withDefaultNamespace("overworld"),
                DarkmatterVisualPacket.Kind.DISASSEMBLE, 42, true, new Vec3(20, 65, -12),
                .6f, 1.8f, 0, 0, false, 42, null, 4, 1, 2, 0, new Vec3(0, 0, 1), contact);
        var buffer = Unpooled.buffer();
        try {
            DarkmatterVisualPacket.CODEC.encode(buffer, packet);
            var copy = DarkmatterVisualPacket.CODEC.decode(buffer);
            assertEquals(contact, copy.surfaceContact);
            assertEquals(4, copy.alpha);
            assertEquals(1, copy.beta);
            assertEquals(2, copy.gamma);
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void invalidOrUnrelatedSurfaceContactsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DarkmatterVisualPacket.SurfaceContact(Vec3.ZERO, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new DarkmatterVisualPacket.SurfaceContact(new Vec3(200, 0, 0), new Vec3(0, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> new DarkmatterVisualPacket.SurfaceContact(new Vec3(Double.NaN, 0, 0), new Vec3(0, 1, 0)));
        var contact = new DarkmatterVisualPacket.SurfaceContact(Vec3.ZERO, new Vec3(0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new DarkmatterVisualPacket(Identifier.withDefaultNamespace("overworld"),
                DarkmatterVisualPacket.Kind.REPAIR, 1, true, Vec3.ZERO, 1, 2, 0, 1, false, 42,
                null, 1, 1, 0, 0, new Vec3(0, 0, 1), contact));
    }
}
