package org.academy.api.common.ability;

import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StartLevelDevPacketTest {
    @Test
    void requestModesRoundTrip() {
        for (var mode : StartLevelDevPacket.Mode.values()) {
            var packet = new StartLevelDevPacket(123456789L, mode);
            var buffer = Unpooled.buffer();

            StartLevelDevPacket.CODEC.encode(buffer, packet);
            var decoded = StartLevelDevPacket.CODEC.decode(buffer);

            assertEquals(123456789L, decoded.getUserPos());
            assertEquals(mode, decoded.getMode());
        }
    }

    @Test
    void legacyConstructorDefaultsToDirectMode() {
        assertEquals(StartLevelDevPacket.Mode.DIRECT, new StartLevelDevPacket(1L).getMode());
    }

    @Test
    void portableDevelopmentSourceRoundTripsHandAndMode() {
        var packet = new StartLevelDevPacket(InteractionHand.OFF_HAND, StartLevelDevPacket.Mode.PREVIEW);
        var buffer = Unpooled.buffer();

        StartLevelDevPacket.CODEC.encode(buffer, packet);
        var decoded = StartLevelDevPacket.CODEC.decode(buffer);

        assertTrue(decoded.getSource().portable());
        assertEquals(InteractionHand.OFF_HAND, decoded.getSource().hand());
        assertNull(decoded.getSource().blockPos());
        assertEquals(StartLevelDevPacket.Mode.PREVIEW, decoded.getMode());
    }

    @Test
    void responseStatusesAndRecommendationRoundTrip() {
        var categoryId = Identifier.parse("academy:accelerator");
        var responses = new StartLevelDevPacket.Response[]{
                new StartLevelDevPacket.Response(true, "Started"),
                new StartLevelDevPacket.Response(
                        StartLevelDevPacket.Response.Status.CONFIRMATION_REQUIRED,
                        "Confirm",
                        categoryId
                ),
                new StartLevelDevPacket.Response(false, "Rejected")
        };

        for (var response : responses) {
            var buffer = Unpooled.buffer();
            StartLevelDevPacket.Response.CODEC.encode(buffer, response);
            var decoded = StartLevelDevPacket.Response.CODEC.decode(buffer);

            assertEquals(response.getStatus(), decoded.getStatus());
            assertEquals(response.getMessage(), decoded.getMessage());
            assertEquals(response.getRecommendedCategory(), decoded.getRecommendedCategory());
            assertEquals(response.isSuccess(), decoded.isSuccess());
            assertEquals(response.requiresConfirmation(), decoded.requiresConfirmation());
        }
    }
}
