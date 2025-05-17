package org.academy.api.server.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraft;
import org.academy.api.common.annotation.PacketHandler;
import org.academy.api.common.network.FriendlyByteBufDeserializer;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.packet.C2SPacket;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;

public final class NetworkSystemServer {
    private static boolean initialized = false;
    private static final List<Class<?>> SERVER_PACKET_HANDLER_CLASSES = new ArrayList<>();
    private static final Map<String, C2SPacketHandler> C2S_PACKET_HANDLER_MAP = new HashMap<>();

    public static void init() {
        FutureManagerServer.register();
        initAnnotation();
        initialized = true;
    }

    public static void registerPacketHandlerClass(Class<?> clazz) {
        if (!initialized) SERVER_PACKET_HANDLER_CLASSES.add(clazz);
    }

    public static void initAnnotation() {
        for (Class<?> clazz : SERVER_PACKET_HANDLER_CLASSES) {
            Method[] methods = clazz.getDeclaredMethods();

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            for (Method method : methods) {
                if (method.isAnnotationPresent(PacketHandler.class)) {
                    if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                        String packetString = method.getAnnotation(PacketHandler.class).packet();
                        MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
                        try {
                            MethodHandle mh = lookup.findStatic(clazz, method.getName(), methodType);
                            Class<?>[] parameterTypes = method.getParameterTypes();
                            int parameterCount = parameterTypes.length;
                            List<BiFunction<ServerGamePacketListenerImpl, C2SPacket, Object>>
                                    biFunctions = new ArrayList<>(Collections.nCopies(parameterCount, null));
                            for (int i = 0; i < parameterCount; i++) {
                                Class<?> parameterType = parameterTypes[i];
                                if (parameterType.equals(ServerPlayer.class)) {
                                    biFunctions.set(i, (
                                            listener, c2SPacket) ->
                                            listener.player);
                                } else if (parameterType.equals(ServerGamePacketListenerImpl.class)) {
                                    biFunctions.set(i, (
                                            listener, c2SPacket) ->
                                            listener);
                                } else if (parameterType.equals(C2SPacket.class)) {
                                    biFunctions.set(i, (
                                            listener, c2SPacket) ->
                                            c2SPacket);
                                } else if (parameterType.equals(ServerLevel.class)) {
                                    biFunctions.set(i, (
                                            listener, c2SPacket) ->
                                            listener.player.level());
                                } else {
                                    FriendlyByteBufDeserializer<?> deserializer = FriendlyByteBufDeserializers.getRequiredDeserializer(parameterType);
                                    biFunctions.set(i, (listener, c2SPacket) ->
                                            deserializer.deserialize(c2SPacket.friendlyByteBuf)
                                    );
                                }
                            }
                            registerC2SPacketHandler(packetString, (listener, packet) -> {
                                try {
                                    Object[] args = new Object[parameterCount];
                                    for (int i = 0; i < parameterCount; i++) {
                                        BiFunction<ServerGamePacketListenerImpl, C2SPacket, Object> biFunction = biFunctions.get(i);
                                        Object result = biFunction.apply(listener, packet);
                                        args[i] = result;
                                    }
                                    mh.invokeWithArguments(args);
                                } catch (Throwable throwable) {
                                    AcademyCraft.LOGGER.warn(throwable.getMessage());
                                }
                            });
                        } catch (NoSuchMethodException | IllegalAccessException exception) {
                            AcademyCraft.LOGGER.warn(exception.getMessage());
                        }
                    } else {
                        AcademyCraft.LOGGER.info("Method must be static and public.");
                    }
                }
            }
        }
    }

    private NetworkSystemServer() {
    }

    public static void registerC2SPacketHandler(String packet, C2SPacketHandler handler) {
        if (!initialized) C2S_PACKET_HANDLER_MAP.put(packet, handler);
    }

    @Nullable
    public static C2SPacketHandler getC2SPacketHandler(String packet) {
        return C2S_PACKET_HANDLER_MAP.get(packet);
    }
}