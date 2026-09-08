package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.vfx.SkillVfxState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillVfxPacketTest {
    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private SkillVfxState roundTrip(SkillVfxState state) {
        var buffer = Unpooled.buffer();
        try {
            SkillVfxPacket.CODEC.encode(buffer, new SkillVfxPacket(DIMENSION, 123, 7, state));
            var decoded = SkillVfxPacket.CODEC.decode(buffer);
            assertEquals(DIMENSION, decoded.dimension);
            assertEquals(123, decoded.id);
            assertEquals(7, decoded.revision);
            assertEquals(0, buffer.readableBytes());
            return decoded.state;
        } finally { buffer.release(); }
    }

    @Test void smokeAndSlashCarryAppearanceInOneSmallPacket() {
        for (var state : new SkillVfxState[]{
                new SkillVfxState.Smoke(new Vec3(2, 3, 4), .5f, .7f, 3, 80),
                new SkillVfxState.Slash(new Vec3(2, 3, 4), -20, 270, 2, -1, 4)}) {
            assertEquals(state, roundTrip(state));
            assertTrue(state.oneShot());
            var buffer = Unpooled.buffer();
            try {
                SkillVfxPacket.CODEC.encode(buffer, new SkillVfxPacket(DIMENSION, 1, 1, state));
                assertTrue(buffer.readableBytes() <= 72);
            } finally { buffer.release(); }
        }
    }

    @Test void rejectsInvalidPureVisualParameters() {
        for (var state : new SkillVfxState[]{
                new SkillVfxState.Smoke(Vec3.ZERO, Float.NaN, .5f, 0, 80),
                new SkillVfxState.Smoke(Vec3.ZERO, 1, .8f, 0, 80),
                new SkillVfxState.Smoke(Vec3.ZERO, 1, .5f, 4, 80),
                new SkillVfxState.Smoke(Vec3.ZERO, 1, .5f, 0, 0),
                new SkillVfxState.Slash(Vec3.ZERO, Float.NaN, 0, 1, 1, 4),
                new SkillVfxState.Slash(Vec3.ZERO, 0, 0, 33, 1, 4),
                new SkillVfxState.Slash(Vec3.ZERO, 0, 0, 1, 1, 201)}) {
            assertThrows(IllegalArgumentException.class, () -> roundTrip(state));
        }
    }

    @Test void reflectedBeamIsSelfContained() {
        var beam = new SkillVfxState.Beam(new Vec3(123456, 90, -54321), 25, 90,
                50, 1.2f, 0.3f, 40, 40, 15, 9, 12, 38, new Vec3(-1, 0, 0), 1);
        assertEquals(beam, roundTrip(beam));
    }

    @Test void chargingAndFlyingPlasmaHaveIndependentSchemas() {
        var p = new Vec3(10, 95, 10);
        var origin = new Vec3(4, 64, 10);
        var charge = new SkillVfxState.Plasma(p, origin, p, 0.5f, 0, 0, false, 1, 1f / 240);
        assertEquals(charge, roundTrip(charge));
        var flight = new SkillVfxState.Plasma(p, p, new Vec3(100, 64, 100), 1, 2.5f, 4, true, 0, 0);
        assertEquals(flight, roundTrip(flight));
    }

    @Test void burstAndEndDoNotRequireAnEntity() {
        var burst = new SkillVfxState.Burst(Vec3.ZERO, new Vec3(0, 1, 0), 24, 6, 7, false);
        assertEquals(burst, roundTrip(burst));
        assertEquals(new SkillVfxState.End(Vec3.ZERO, true), roundTrip(new SkillVfxState.End(Vec3.ZERO, true)));
        assertEquals(new SkillVfxState.End(Vec3.ZERO, false), roundTrip(new SkillVfxState.End(Vec3.ZERO, false)));
        var impact = new SkillVfxState.Burst(Vec3.ZERO, Vec3.ZERO, 30, 1, 60, true);
        assertEquals(impact, roundTrip(impact));
    }

    @Test void shortBurstFitsSmallApplicationPacket() {
        var buffer = Unpooled.buffer();
        try {
            SkillVfxPacket.CODEC.encode(buffer, new SkillVfxPacket(DIMENSION, 1, 1,
                    new SkillVfxState.Burst(Vec3.ZERO, new Vec3(0, 1, 0), 5, 1, 4, false)));
            assertTrue(buffer.readableBytes() <= 96);
        } finally { buffer.release(); }
    }

    @Test void rejectsNonFiniteGeometryAndUnboundedLifetimes() {
        assertThrows(IllegalArgumentException.class, () -> roundTrip(new SkillVfxState.Burst(
                new Vec3(Double.NaN, 0, 0), Vec3.ZERO, 5, 1, 4, false)));
        assertThrows(IllegalArgumentException.class, () -> roundTrip(new SkillVfxState.Burst(
                Vec3.ZERO, Vec3.ZERO, 5, 1, Integer.MAX_VALUE, false)));
    }
}
