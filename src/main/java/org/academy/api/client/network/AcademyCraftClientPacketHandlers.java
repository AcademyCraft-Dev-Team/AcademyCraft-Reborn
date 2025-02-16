package org.academy.api.client.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.common.network.AcademyCraftFriendlyByteBufParser;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class AcademyCraftClientPacketHandlers {
    public static final Map<ResourceLocation, ClientHandler> HANDLER_MAP = new HashMap<>();

    static {
        HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.S2C_RESPONSE, (listener, packet) -> {
            FriendlyByteBuf friendlyByteBuf = packet.getData();
            String identifier = friendlyByteBuf.readUtf();
            AcademyCraft.LOGGER.info("Debug 01");
            if (AcademyCraftFriendlyByteBufParser.FRIENDLY_BYTE_BUF_PARSER_MAP.containsKey(identifier)) {
                ResourceLocation key = friendlyByteBuf.readResourceLocation();
                AcademyCraft.LOGGER.info("Debug 02");
                if (NetworkSystemClient.CLIENT_RESPONSE_MAP.containsKey(key)) {
                    AcademyCraft.LOGGER.info("Debug 03");
                    Response response = NetworkSystemClient.CLIENT_RESPONSE_MAP.get(key);
                    AcademyCraft.LOGGER.info("Debug 04");
                    AcademyCraftFriendlyByteBufParser.FRIENDLY_BYTE_BUF_PARSER_MAP.get(identifier).parse(friendlyByteBuf, response);
                    AcademyCraft.LOGGER.info("Debug 05");
                    if (response.runnable != null) {
                        AcademyCraft.LOGGER.info("Debug 06");
                        response.runnable.run();
                    }
                    AcademyCraft.LOGGER.info("Done");
                } else {
                    AcademyCraft.LOGGER.info("Debug 07");
                    throw new NoSuchElementException("Response for key '" + key + "' not found. Ensure the key is registered before sending a response.");
                }
            } else {
                AcademyCraft.LOGGER.info("Debug 08");
                throw new NoSuchElementException("FriendlyByteBuf.parse() returned null for identifier '" + identifier + "'");
            }
        });
        HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.S2C_REQUEST, (listener, packet) -> {
            ResourceLocation key = packet.getData().readResourceLocation();
            if (AcademyCraftClientResponseHandlers.RESPONSE_MAP.containsKey(key)) {
                AcademyCraftClientResponseHandlers.RESPONSE_MAP.get(key).handle(listener);
            } else {
                throw new NoSuchElementException("Response Handler " + key + " not found");
            }
        });
    }

    private AcademyCraftClientPacketHandlers() {
    }
}
