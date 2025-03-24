package org.academy.api.common.network;

import net.minecraft.network.ConnectionProtocol;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.common.network.packet.ClientToServerPacket;
import org.academy.api.common.network.packet.ServerToClientPacket;
import org.academy.api.server.network.packet.S2CResponsePacket;

public class AcademyCraftNetworkSystem {
    public static void init() {
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(S2CResponsePacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(C2SRequestPacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(ClientToServerPacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(ServerToClientPacket.class, ConnectionProtocol.PLAY);
    }
}