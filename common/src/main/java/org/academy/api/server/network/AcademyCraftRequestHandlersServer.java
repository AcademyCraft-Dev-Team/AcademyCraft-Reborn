package org.academy.api.server.network;

import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraftServer;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufIdentifiers;
import org.academy.api.server.network.packet.S2CResponsePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AcademyCraftRequestHandlersServer {
    public static final Map<ResourceLocation, AcademyCraftRequestHandlerServer> REQUEST_HANDLER_MAP = new HashMap<>();

    static {
        REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_GET_LEARNED_SKILL_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            List<String> list = new ArrayList<>();
            list.add(FriendlyByteBufIdentifiers.STRING);
            list.addAll(AcademyCraftServer.academyCraftWorldData.getPlayers().get(serverGamePacketListenerImpl.getPlayer().getUUID()).getSkills());
            serverGamePacketListenerImpl.send(new S2CResponsePacket(FriendlyByteBufIdentifiers.LIST, AcademyCraftNetworkResourceLocations.S2C_GET_LEARNED_SKILL_RESPONSE, list));
        });
        REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_LEARN_CURRICULUM_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            serverGamePacketListenerImpl.send(new S2CResponsePacket(FriendlyByteBufIdentifiers.BOOLEAN, AcademyCraftNetworkResourceLocations.S2C_LEARN_CURRICULUM_RESPONSE, List.of(true)));
        });
    }

    private AcademyCraftRequestHandlersServer() {
    }
}