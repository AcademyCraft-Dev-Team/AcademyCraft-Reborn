package org.academy.api.server.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufParsers;
import org.academy.api.common.network.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class AcademyCraftPacketHandlersServer {
    public static final Map<ResourceLocation, AcademyCraftPacketHandlerServer> HANDLER_MAP = new HashMap<>();

    static {
        HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_REQUEST, (listener, packet) -> {
            ResourceLocation key = packet.getData().readResourceLocation();
            if (AcademyCraftRequestHandlersServer.REQUEST_HANDLER_MAP.containsKey(key)) {
                AcademyCraftRequestHandlersServer.REQUEST_HANDLER_MAP.get(key).handle(listener, packet);
            } else {
                throw new NoSuchElementException("Response Handler " + key + " not found");
            }
        });
        HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_RESPONSE, (listener, packet) -> {
            FriendlyByteBuf friendlyByteBuf = packet.getData();
            String identifier = friendlyByteBuf.readUtf();
            if (FriendlyByteBufParsers.FRIENDLY_BYTE_BUF_PARSER_MAP.containsKey(identifier)) {
                ResourceLocation key = friendlyByteBuf.readResourceLocation();
                if (AcademyCraftNetworkSystemServer.SERVER_RESPONSE_MAP.containsKey(key)) {
                    Response response = AcademyCraftNetworkSystemServer.SERVER_RESPONSE_MAP.get(key);
                    FriendlyByteBufParsers.FRIENDLY_BYTE_BUF_PARSER_MAP.get(identifier).parse(friendlyByteBuf, response);
                    if (response.runnable != null) {
                        response.runnable.run();
                    }
                } else {
                    throw new NoSuchElementException("Response for key '" + key + "' not found. Ensure the key is registered before sending a response.");
                }
            } else {
                throw new NoSuchElementException("FriendlyByteBuf.parse() returned null for identifier '" + identifier + "'");
            }
        });
    }

    private AcademyCraftPacketHandlersServer() {
    }
}