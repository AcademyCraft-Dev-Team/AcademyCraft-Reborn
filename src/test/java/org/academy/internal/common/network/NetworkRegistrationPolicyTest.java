package org.academy.internal.common.network;

import net.minecraft.network.PacketListener;
import org.junit.jupiter.api.Test;
import org.misaka.api.common.network.NetworkManager;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkRegistrationPolicyTest {
    @Test
    void replacingAStaticListenerPreventsAccumulatedPacketHandlers() {
        var manager = new NetworkManager();
        TestListener.handled = 0;

        manager.register(TestListener.class);
        NetworkRegistrationPolicy.replaceStaticRegistration(manager, TestListener.class);
        manager.register(TestListener.class);
        manager.dispatchPacket(new TestPacket());

        assertEquals(1, TestListener.handled);
    }

    public static final class TestListener {
        private static int handled;

        @SubscribePacket
        public static void handle(TestPacket ignored) {
            handled++;
        }
    }

    public static final class TestPacket extends Packet<PacketListener, TestPacket> {
        @Override
        public PacketType<PacketListener, TestPacket> getPacketType() {
            return null;
        }
    }
}
