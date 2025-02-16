package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.network.packet.C2SResponsePacket;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AcademyCraftClientResponseHandlers {
    public static final Map<ResourceLocation, ClientResponseHandler> RESPONSE_MAP = new HashMap<>();

    static {
        RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.S2C_CHANGE_ABILITY_CATEGORY_REQUEST, listener -> NetworkSystemClient.sendPacket(new C2SResponsePacket("boolean", AcademyCraftNetworkResourceLocations.C2S_CHANGE_ABILITY_CATEGORY_RESPONSE, List.of(true))));
        RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.S2C_GET_ALL_SKILL_RESPONSE, new ClientResponseHandler() {
            @Override
            public void handle(ClientPacketListener listener) {

            }
        });
    }

    private AcademyCraftClientResponseHandlers() {
    }
}