package org.academy.api.client.network;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.Response;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftNetworkSystemClient {
    public static final Map<ResourceLocation, Response> CLIENT_RESPONSE_MAP = new HashMap<>();
    public static Connection connection;

    public static void sendPacket(Packet<?> packet) {
        connection.send(packet);
    }

    private AcademyCraftNetworkSystemClient() {
    }
}