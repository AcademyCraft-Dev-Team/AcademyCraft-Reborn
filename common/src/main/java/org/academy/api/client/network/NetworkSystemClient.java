package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.FriendlyByteBufDeserializer;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.packet.S2CPacket;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class NetworkSystemClient {
    public static final Map<ResourceLocation, S2CPacketHandler> SERVER_TO_CLIENT_PACKET_HANDLER_MAP = new HashMap<>();
    public static Connection connection;

    static {
        FutureManagerClient.register();
    }

    private NetworkSystemClient() {
    }

    public static void sendPacket(Packet<?> packet) {
        connection.send(packet);
    }

    public static void registerS2CPacketHandler(ResourceLocation resourceLocation, S2CPacketHandler handler) {
        SERVER_TO_CLIENT_PACKET_HANDLER_MAP.put(resourceLocation, handler);
    }

    /**
     * 根据 Method 参数自动生成序列化
     *
     * @param resourceLocation 数据包 ResourceLocation
     * @param method           需要调用的 Method
     * @param consumer         负责将生成的参数传入方法,不直接使用 Method.invoke 的原因是性能
     */
    public static void registerS2CPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Method method, @NotNull Consumer<Object[]> consumer) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        registerS2CPacketHandler(resourceLocation, parameterTypes, consumer);
    }

    public static <T> void registerS2CPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Class<T> clazz, @NotNull Consumer<T> consumer) {
        registerS2CPacketHandler(resourceLocation, new Class<?>[]{clazz}, objects -> consumer.accept((T) objects[0]));
    }

    public static void registerS2CPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Class[] parameterTypes, @NotNull Consumer<Object[]> consumer) {
        final byte parameterCount = (byte) parameterTypes.length;
        final List<BiFunction<ClientPacketListener, S2CPacket, Object>> biFunctions =
                new ArrayList<>(Collections.nCopies(parameterCount, null));
        for (byte i = 0; i < parameterCount; i++) {
            Class parameterType = parameterTypes[i];
            if (parameterType.equals(ClientPacketListener.class)) {
                biFunctions.set(i, (
                        listener, packet) ->
                        listener);
            } else if (parameterType.equals(S2CPacket.class)) {
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
        registerS2CPacketHandler(resourceLocation, (listener, packet) -> {
            Object[] args = new Object[parameterCount];
            for (byte i = 0; i < parameterCount; i++) {
                args[i] = biFunctions.get(i).apply(listener, packet);
            }
            consumer.accept(args);
        });
    }

    public static void registerS2CPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull BiFunction<ClientPacketListener, S2CPacket, Object>[] biFunctions, @NotNull Consumer<Object[]> consumer) {
        registerS2CPacketHandler(resourceLocation, (listener, packet) -> {
            Object[] args = new Object[biFunctions.length];
            for (int i = 0; i < biFunctions.length; i++) {
                args[i] = biFunctions[i].apply(listener, packet);
            }
            consumer.accept(args);
        });
    }

    public static void registerS2CPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull FriendlyByteBufDeserializer[] friendlyByteBufDeserializers, @NotNull Consumer<Object[]> consumer) {
        registerS2CPacketHandler(resourceLocation, (listener, packet) -> {
            Object[] args = new Object[friendlyByteBufDeserializers.length];
            FriendlyByteBuf friendlyByteBuf = packet.friendlyByteBuf;
            for (int i = 0; i < friendlyByteBufDeserializers.length; i++) {
                FriendlyByteBufDeserializer deserializer = friendlyByteBufDeserializers[i];
                args[i] = deserializer.deserialize(friendlyByteBuf);
            }
            consumer.accept(args);
        });
    }
}