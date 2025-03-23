package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.util.ReflectionUtil;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.Response;
import org.academy.api.common.network.packet.ServerToClientPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;

public class AcademyCraftNetworkSystemClient {
    public static final Map<ResourceLocation, ServerToClientPacketHandler> SERVER_TO_CLIENT_PACKET_HANDLER_MAP = new HashMap<>();
    public static final Map<ResourceLocation, Response> CLIENT_RESPONSE_MAP = new HashMap<>();
    public static Connection connection;

    public static void sendPacket(Packet<?> packet) {
        connection.send(packet);
    }

    public static void registerClientToServerPacketHandler(ResourceLocation resourceLocation, ServerToClientPacketHandler handler) {
        SERVER_TO_CLIENT_PACKET_HANDLER_MAP.put(resourceLocation, handler);
    }

    /**
     * 根据 Method 参数自动生成序列化
     *
     * @param resourceLocation 数据包 ResourceLocation
     * @param method           需要调用的 Method
     * @param instance         如果静态方法则为 Null
     */
    public static void registerServerToClientPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Method method, @Nullable Object instance) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        final byte parameterCount = (byte) parameterTypes.length;
        final List<BiFunction<ClientPacketListener, ServerToClientPacket, Object>> biFunctions =
                new ArrayList<>(Collections.nCopies(parameterCount, null));
        for (byte i = 0; i < parameterCount; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.equals(ClientPacketListener.class)) {
                biFunctions.set(i, (
                        listener, packet) ->
                        listener);
            } else if (parameterType.equals(ServerToClientPacket.class)) {
                biFunctions.set(i, (
                        listener, packet) ->
                        packet);
            } else {
                biFunctions.set(i, (listener, packet) ->
                        FriendlyByteBufDeserializers.getRequiredDeserializer(parameterType)
                                .deserialize(packet.friendlyByteBuf)
                );
            }
        }
        registerClientToServerPacketHandler(resourceLocation, (listener, packet) -> {
            Object[] args = new Object[parameterCount];
            for (byte i = 0; i < parameterCount; i++) {
                args[i] = biFunctions.get(i).apply(listener, packet);
            }
            ReflectionUtil.invokeMethodWithArgs(method, instance, args);
        });
    }

    private AcademyCraftNetworkSystemClient() {
    }
}