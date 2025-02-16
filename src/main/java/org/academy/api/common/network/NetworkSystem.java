package org.academy.api.common.network;

import net.minecraft.network.ConnectionProtocol;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.client.network.packet.C2SResponsePacket;
import org.academy.api.server.network.packet.S2CRequestPacket;
import org.academy.api.server.network.packet.S2CResponsePacket;

public class NetworkSystem {
    public static void init() {
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(S2CResponsePacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(S2CRequestPacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(C2SResponsePacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(C2SRequestPacket.class, ConnectionProtocol.PLAY);
    }
}