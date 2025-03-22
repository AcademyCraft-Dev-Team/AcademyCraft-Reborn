package org.academy.api.client.network;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.Response;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class AcademyCraftNetworkSystemClient {
    public static final Map<ResourceLocation, ServerToClientPacketHandler> SERVER_TO_CLIENT_PACKET_HANDLER_MAP = new HashMap<>();
    public static final Map<ResourceLocation, Response> CLIENT_RESPONSE_MAP = new HashMap<>();
    public static Connection connection;

    public static void sendPacket(Packet<?> packet) {
        connection.send(packet);
    }

    public static void registerPacketHandler(ResourceLocation resourceLocation, Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.isAssignableFrom(Connection.class)) {}
        }
    }

    private AcademyCraftNetworkSystemClient() {
    }
}