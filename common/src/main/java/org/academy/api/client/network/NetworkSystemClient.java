package org.academy.api.client.network;

import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.academy.AcademyCraft;
import org.academy.api.client.network.future.FutureManagerClient;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketHandler;
import org.academy.api.common.network.packet.IPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NetworkSystemClient {
    public static Connection connection;
    private static final ConcurrentHashMap<Class<? extends IPacket<?>>, List<PacketHandler>> CLIENT_TYPED_LISTENERS = new ConcurrentHashMap<>();
    private static final Map<Object, List<PacketHandler>> CLIENT_LISTENERS_BY_TARGET = new MapMaker().weakKeys().makeMap();
    private static final ReadWriteLock clientLock = new ReentrantReadWriteLock();

    public static void init() {
        FutureManagerClient.init();
    }

    public static void registerPacketListener(@NotNull Class<?> targetClass) {
        registerPacketListenerInternal(targetClass, null);
    }

    public static void registerPacketListener(@NotNull Object targetInstance) {
        registerPacketListenerInternal(targetInstance.getClass(), targetInstance);
    }

    public static void registerPacketListener(@NotNull PacketHandler packetHandler) {
        clientLock.writeLock().lock();
        try {
            CLIENT_LISTENERS_BY_TARGET.computeIfAbsent(packetHandler.getPacketType(), o -> new ArrayList<>()).add(packetHandler);
            CLIENT_TYPED_LISTENERS.computeIfAbsent(packetHandler.getPacketType(), k -> new ArrayList<>()).add(packetHandler);
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    private static void registerPacketListenerInternal(@NotNull Class<?> clazz, @Nullable Object instance) {
        List<PacketHandler> generatedHandlers = NetworkSystem.findPacketHandlers(clazz, instance);

        if (!generatedHandlers.isEmpty()) {
            clientLock.writeLock().lock();
            try {
                Object key = (instance == null) ? clazz : instance;
                CLIENT_LISTENERS_BY_TARGET.put(key, List.copyOf(generatedHandlers));

                for (PacketHandler handler : generatedHandlers) {
                    CLIENT_TYPED_LISTENERS.computeIfAbsent(handler.getPacketType(), k -> new ArrayList<>()).add(handler);
                }
            } finally {
                clientLock.writeLock().unlock();
            }
        }
    }

    public static void unregisterPacketListener(@NotNull Class<?> targetClass) {
        unregisterPacketListenerInternal(targetClass);
    }

    public static void unregisterPacketListener(@NotNull Object targetInstance) {
        unregisterPacketListenerInternal(targetInstance);
    }

    private static void unregisterPacketListenerInternal(@NotNull Object keyToRemove) {
        clientLock.writeLock().lock();
        try {
            List<PacketHandler> handlersToRemove = CLIENT_LISTENERS_BY_TARGET.remove(keyToRemove);
            if (handlersToRemove != null) {
                for (PacketHandler handler : handlersToRemove) {
                    List<PacketHandler> typedList = CLIENT_TYPED_LISTENERS.get(handler.getPacketType());
                    if (typedList != null) {
                        typedList.remove(handler);
                        if (typedList.isEmpty()) {
                            CLIENT_TYPED_LISTENERS.remove(handler.getPacketType());
                        }
                    }
                }
            }
        } finally {
            clientLock.writeLock().unlock();
        }
    }

    public static <T extends IPacket<?>> void dispatchClientPacket(T packet) {
        List<PacketHandler> handlers = null;
        clientLock.readLock().lock();
        try {
            List<PacketHandler> typedList = CLIENT_TYPED_LISTENERS.get(packet.getClass());
            if (typedList != null && !typedList.isEmpty()) {
                handlers = Lists.newArrayList(typedList);
            }
        } finally {
            clientLock.readLock().unlock();
        }

        if (handlers != null) {
            for (PacketHandler handler : handlers) {
                try {
                    handler.handlePacket(packet);
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error("Exception dispatching client packet {} to handler {}: {}", packet.getClass().getSimpleName(), handler.getClass().getName(), e.getMessage(), e);
                }
            }
        }
    }


    public static void sendPacket(Packet<?> packet) {
        if (connection != null) {
            connection.send(packet);
        }
    }
}