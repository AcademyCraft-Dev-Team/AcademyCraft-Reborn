package org.academy.api.server.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.Response;
import org.academy.api.common.network.packet.ClientToServerPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;

public class AcademyCraftNetworkSystemServer {
    public static final Map<ResourceLocation, ClientToServerPacketHandler> CLIENT_TO_SERVER_PACKET_HANDLER_MAP = new HashMap<>();
    public static final Map<ResourceLocation, Response> SERVER_RESPONSE_MAP = new HashMap<>();

    public static void registerClientToServerPacketHandler(ResourceLocation resourceLocation, ClientToServerPacketHandler handler) {
        CLIENT_TO_SERVER_PACKET_HANDLER_MAP.put(resourceLocation, handler);
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
        final List<BiFunction<ServerGamePacketListenerImpl, ClientToServerPacket, Object>> biFunctions =
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
            } else if (parameterType.equals(ClientToServerPacket.class)) {
                biFunctions.set(i, (
                        listener, packet) ->
                        packet);
            } else if (parameterType.equals(ServerLevel.class)) {
                biFunctions.set(i, (
                        listener, packet) ->
                        listener.player.level());
            } else {
                biFunctions.set(i, (listener, packet) -> FriendlyByteBufDeserializers.getRequiredDeserializer(parameterType).deserialize(packet.friendlyByteBuf));
            }
        }
        registerClientToServerPacketHandler(resourceLocation, (listener, packet) -> {
            Object[] args = new Object[parameterCount];
            for (byte i = 0; i < parameterCount; i++) {
                args[i] = biFunctions.get(i).apply(listener, packet);
            }
            try {
                method.invoke(instance, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private AcademyCraftNetworkSystemServer() {
    }
}