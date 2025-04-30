package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.academy.AcademyCraft;
import org.academy.api.common.annotation.PacketHandler;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.packet.S2CPacket;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;

public class NetworkSystemClient {
    public static final List<Class<?>> CLIENT_PACKET_HANDLER_CLASSES = new ArrayList<>();
    public static final Map<String, S2CPacketHandler> SERVER_TO_CLIENT_PACKET_HANDLER_MAP = new HashMap<>();
    public static Connection connection;

    public static void init() {
        FutureManagerClient.register();
        initAnnotation();
    }

    public static void initAnnotation() {
        for (Class<?> clazz : CLIENT_PACKET_HANDLER_CLASSES) {
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
                            List<BiFunction<ClientPacketListener, S2CPacket, Object>> biFunctions =
                                    new ArrayList<>(Collections.nCopies(parameterCount, null));
                            for (int i = 0; i < parameterCount; i++) {
                                Class<?> parameterType = parameterTypes[i];
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
                            registerS2CPacketHandler(packetString, (listener, packet) -> {
                                try {
                                    Object[] args = new Object[parameterCount];
                                    for (int i = 0; i < parameterCount; i++) {
                                        args[i] = biFunctions.get(i).apply(listener, packet);
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

    private NetworkSystemClient() {
    }

    public static void sendPacket(Packet<?> packet) {
        connection.send(packet);
    }

    public static void registerS2CPacketHandler(String packet, S2CPacketHandler handler) {
        SERVER_TO_CLIENT_PACKET_HANDLER_MAP.put(packet, handler);
    }
}