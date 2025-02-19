package org.academy.api.server.network;

import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.Response;

import java.util.HashMap;
import java.util.Map;

public class NetworkSystemServer {
    public static final Map<ResourceLocation, Response> SERVER_RESPONSE_MAP = new HashMap<>();

    private NetworkSystemServer() {
    }
}