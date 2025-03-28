package org.academy.api.common.network;

import net.minecraft.network.ConnectionProtocol;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.S2CPacket;

public class NetworkSystem {
    public static void init() {
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(C2SPacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(S2CPacket.class, ConnectionProtocol.PLAY);
    }
}