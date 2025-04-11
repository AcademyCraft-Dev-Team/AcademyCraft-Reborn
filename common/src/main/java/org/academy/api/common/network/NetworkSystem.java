package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.network.S2CPacketHandler;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.util.GameUtil;
import org.academy.api.server.network.C2SPacketHandler;
import org.academy.api.server.network.NetworkSystemServer;

import javax.annotation.Nullable;

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

    public static ResourceLocation registerPacket(ResourceLocation resourceLocation, @Nullable C2SPacketHandler c2sPacketHandler, @Nullable S2CPacketHandler s2cPacketHandler) {
        registerPacketHandler(resourceLocation,c2sPacketHandler, s2cPacketHandler);
        return registerPacket(resourceLocation);
    }

    public static void registerPacketHandler(ResourceLocation resourceLocation,@Nullable C2SPacketHandler c2sPacketHandler, @Nullable S2CPacketHandler s2cPacketHandler) {
        switch (GameUtil.getEnvType()){
            case CLIENT -> {
                if (s2cPacketHandler == null) {
                    AcademyCraft.LOGGER.warn("NetworkSystem: Client side packet handler is null!");
                }
                NetworkSystemClient.registerS2CPacketHandler(resourceLocation, s2cPacketHandler);
            }
            case SERVER -> {
                if (c2sPacketHandler == null) {
                    AcademyCraft.LOGGER.warn("NetworkSystem: Serverside packet handler is null!");
                }
                NetworkSystemServer.registerC2SPacketHandler(resourceLocation, c2sPacketHandler);
            }
        }
    }

    public static int getPacketId(ResourceLocation resourceLocation) {
        return PACKET_IDS.get(resourceLocation);
    }

    public static ResourceLocation getPacketResourceLocation(int packetId) {
        return PACKET_IDS.inverse().get(packetId);
    }
}