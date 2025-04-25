package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.jetbrains.annotations.Nullable;

public class NetworkSystem {
    public static final BiMap<ResourceLocation, Integer> PACKET_IDS = HashBiMap.create();

    public static void init() {
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(C2SPacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(S2CPacket.class, ConnectionProtocol.PLAY);
    }

    public static ResourceLocation registerPacket(ResourceLocation resourceLocation) {
        PACKET_IDS.put(resourceLocation, PACKET_IDS.size());
        return resourceLocation;
    }

    public static int getPacketId(ResourceLocation resourceLocation) {
        return PACKET_IDS.get(resourceLocation);
    }

    @Nullable
    public static ResourceLocation getPacketResourceLocation(int packetId) {
        return PACKET_IDS.inverse().get(packetId);
    }
}