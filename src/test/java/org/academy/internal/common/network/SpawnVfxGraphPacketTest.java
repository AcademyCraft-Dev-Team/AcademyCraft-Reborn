package org.academy.internal.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnVfxGraphPacketTest {
    private static SpawnVfxGraphPacket roundTrip(SpawnVfxGraphPacket packet) {
        var buf = Unpooled.buffer();
        SpawnVfxGraphPacket.CODEC.encode(buf, packet);
        return SpawnVfxGraphPacket.CODEC.decode(buf);
    }

    @Test
    void codecRoundTripsAllFields() {
        var packet = new SpawnVfxGraphPacket(
                Identifier.fromNamespaceAndPath("academy", "vfxgraph/minimal_burst"),
                new Vec3(1.5, -2.0, 3.25), new Vec3(0.25, 0.5, -1.0),
                -1, 1.5f, 2.25f,
                Map.of("size", 0.35f, "radius", 8f)
        );

        var decoded = roundTrip(packet);

        assertEquals(packet.assetId(), decoded.assetId());
        assertEquals(packet.position().x, decoded.position().x, 1e-6f);
        assertEquals(packet.position().y, decoded.position().y, 1e-6f);
        assertEquals(packet.position().z, decoded.position().z, 1e-6f);
        assertEquals(packet.direction().x, decoded.direction().x, 1e-6f);
        assertEquals(packet.direction().y, decoded.direction().y, 1e-6f);
        assertEquals(packet.direction().z, decoded.direction().z, 1e-6f);
        assertEquals(packet.followEntityId(), decoded.followEntityId());
        assertEquals(packet.scale(), decoded.scale(), 1e-6f);
        assertEquals(packet.lifetimeSeconds(), decoded.lifetimeSeconds(), 1e-6f);
        assertEquals(packet.floatParams(), decoded.floatParams());
    }

    @Test
    void codecRoundTripsFollowEntityAndEmptyParams() {
        var packet = new SpawnVfxGraphPacket(
                Identifier.fromNamespaceAndPath("academy", "vfxgraph/demo_burst"),
                new Vec3(0, 64, 0), new Vec3(0, 1, 0), 42, 1f, 3f, Map.of()
        );

        var decoded = roundTrip(packet);

        assertEquals(42, decoded.followEntityId());
        assertEquals(Map.of(), decoded.floatParams());
    }

    @Test
    void clientHandlerInitializationIsIdempotent() throws ReflectiveOperationException {
        SpawnVfxGraphPacket.initClient();
        SpawnVfxGraphPacket.initClient();

        var initialized = SpawnVfxGraphPacket.class.getDeclaredField("clientInitialized");
        initialized.setAccessible(true);
        assertTrue(initialized.getBoolean(null));
    }
}
