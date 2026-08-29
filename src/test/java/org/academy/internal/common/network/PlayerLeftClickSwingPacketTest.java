package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLeftClickSwingPacketTest {
    @Test
    void unitPacketRoundTripsAsTheSingleton() {
        var buffer = Unpooled.buffer();

        PlayerLeftClickSwingPacket.CODEC.encode(buffer, PlayerLeftClickSwingPacket.INSTANCE);

        assertSame(PlayerLeftClickSwingPacket.INSTANCE, PlayerLeftClickSwingPacket.CODEC.decode(buffer));
    }

    @Test
    void onlyLeftClickSwingSitesSendTheExplicitInput() throws IOException {
        var clientMixin = Files.readString(Path.of(
                "src/main/java/org/academy/mixin/client/MixinMinecraft.java"));
        var commonMixin = Files.readString(Path.of(
                "src/main/java/org/academy/mixin/common/MixinLivingEntity.java"));
        var sendCall = "MisakaNetworkClient.send(PlayerLeftClickSwingPacket.INSTANCE);";

        assertTrue(clientMixin.contains("method = \"startAttack\""));
        assertTrue(clientMixin.contains("method = \"continueAttack\""));
        assertTrue(clientMixin.contains(
                "LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"));
        assertEquals(2, occurrences(clientMixin, sendCall));
        assertFalse(commonMixin.contains("academy$onAdvancedWingSwing"));
        assertFalse(commonMixin.contains("method = \"swing(Lnet/minecraft/world/InteractionHand;Z)V\""));
    }

    private static int occurrences(String source, String search) {
        var count = 0;
        var offset = 0;
        while ((offset = source.indexOf(search, offset)) >= 0) {
            count++;
            offset += search.length();
        }
        return count;
    }
}
