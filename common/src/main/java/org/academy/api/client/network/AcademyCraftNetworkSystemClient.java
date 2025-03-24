package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.Response;
import org.academy.api.common.network.packet.ServerToClientPacket;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class AcademyCraftNetworkSystemClient {
    public static final Map<ResourceLocation, ServerToClientPacketHandler> SERVER_TO_CLIENT_PACKET_HANDLER_MAP = new HashMap<>();
    public static final Map<ResourceLocation, Response> CLIENT_RESPONSE_MAP = new HashMap<>();
    public static Connection connection;

    public static void sendPacket(Packet<?> packet) {
        connection.send(packet);
    }

    public static void registerServerToClientPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull ServerToClientPacketHandler handler) {
        SERVER_TO_CLIENT_PACKET_HANDLER_MAP.put(resourceLocation, handler);
    }

    /**
     * 根据 Method 参数自动生成序列化
     *
     * @param resourceLocation 数据包 ResourceLocation
     * @param method           需要调用的 Method
     * @param consumer         负责将生成的参数传入方法,不直接使用 Method.invoke 的原因是性能
     */
    public static void registerServerToClientPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Method method, @NotNull Consumer<Object[]> consumer) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        registerServerToClientPacketHandler(resourceLocation, parameterTypes, consumer);
    }

    @SuppressWarnings("unchecked")
    public static <T> void registerServerToClientPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Class<T> clazz, @NotNull Consumer<T> consumer) {
        registerServerToClientPacketHandler(resourceLocation, new Class<?>[]{clazz}, objects -> consumer.accept((T) objects[0]));
    }

    public static void registerServerToClientPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Class<?>[] parameterTypes, @NotNull Consumer<Object[]> consumer) {
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
        registerServerToClientPacketHandler(resourceLocation, (listener, packet) -> {
            Object[] args = new Object[parameterCount];
            for (byte i = 0; i < parameterCount; i++) {
                args[i] = biFunctions.get(i).apply(listener, packet);
            }
            consumer.accept(args);
        });
    }

    private AcademyCraftNetworkSystemClient() {
    }
}