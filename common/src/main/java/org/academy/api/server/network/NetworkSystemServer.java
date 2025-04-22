package org.academy.api.server.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.packet.C2SPacket;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class NetworkSystemServer {
    public static final Map<ResourceLocation, C2SPacketHandler> C2S_PACKET_HANDLER_MAP = new HashMap<>();

    static {
        FutureManagerServer.register();
    }

    private NetworkSystemServer() {
    }

    public static void registerC2SPacketHandler(ResourceLocation resourceLocation, C2SPacketHandler handler) {
        C2S_PACKET_HANDLER_MAP.put(resourceLocation, handler);
    }

    /**
     * 根据 Method 参数自动生成序列化
     *
     * @param resourceLocation 数据包 ResourceLocation
     * @param method           需要调用的 Method
     * @param consumer         负责将生成的参数传入方法
     */
    public static void registerC2SPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Method method, @NotNull Consumer<Object[]> consumer) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        registerC2SPacketHandler(resourceLocation, parameterTypes, consumer);
    }

    @SuppressWarnings("unchecked")
    public static <T> void registerC2SPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Class<T> clazz, @NotNull Consumer<T> consumer) {
        registerC2SPacketHandler(resourceLocation, new Class[]{clazz}, objects -> consumer.accept((T) objects[0]));
    }

    public static void registerC2SPacketHandler(@NotNull ResourceLocation resourceLocation, @NotNull Class<?>[] parameterTypes, @NotNull Consumer<Object[]> consumer) {
        final byte parameterCount = (byte) parameterTypes.length;
        final List<BiFunction<ServerGamePacketListenerImpl, C2SPacket, Object>> biFunctions =
                new ArrayList<>(Collections.nCopies(parameterCount, null));
        for (byte i = 0; i < parameterCount; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.equals(ServerPlayer.class)) {
                biFunctions.set(i, (
                        listener, packet) ->
                        listener.player);
            } else if (parameterType.equals(ServerGamePacketListenerImpl.class)) {
                biFunctions.set(i, (
                        listener, packet) ->
                        listener);
            } else if (parameterType.equals(C2SPacket.class)) {
                biFunctions.set(i, (
                        listener, packet) ->
                        packet);
            } else if (parameterType.equals(ServerLevel.class)) {
                biFunctions.set(i, (
                        listener, packet) ->
                        listener.player.level());
            } else {
                biFunctions.set(i, (listener, packet) ->
                        FriendlyByteBufDeserializers.getRequiredDeserializer(parameterType)
                                .deserialize(packet.friendlyByteBuf)
                );
            }
        }
        registerC2SPacketHandler(resourceLocation, (listener, packet) -> {
            Object[] args = new Object[parameterCount];
            for (byte i = 0; i < parameterCount; i++) {
                args[i] = biFunctions.get(i).apply(listener, packet);
            }
            consumer.accept(args);
        });
    }
}