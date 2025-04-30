package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.network.ConnectionProtocol;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.jetbrains.annotations.Nullable;

public class NetworkSystem {
    public static final BiMap<String, Integer> PACKET_IDS = HashBiMap.create();

    public static void init() {
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(C2SPacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(S2CPacket.class, ConnectionProtocol.PLAY);
    }

    public static void registerPacket(String packet) {
        if (!PACKET_IDS.containsKey(packet)) {
            PACKET_IDS.put(packet, PACKET_IDS.size());
        }
    }

    public static int getPacketId(String packet) {
        return PACKET_IDS.get(packet);
    }

    @Nullable
    public static String getPacketResourceLocation(int packetId) {
        return PACKET_IDS.inverse().get(packetId);
    }
}