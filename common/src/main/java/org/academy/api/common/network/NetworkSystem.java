package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import net.minecraft.network.ConnectionProtocol;
import org.academy.AcademyCraft;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NetworkSystem {
    private static final Set<Class<? extends IPacket<?>>> REGISTERED_PACKET_TYPES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final BiMap<Class<? extends IPacket<?>>, Integer> CLASS_TO_ID = HashBiMap.create();
    private static final BiMap<Integer, Class<? extends IPacket<?>>> ID_TO_CLASS = CLASS_TO_ID.inverse();
    private static final BiMap<String, Integer> NAME_TO_ID = HashBiMap.create();
    private static final BiMap<Integer, String> ID_TO_NAME = NAME_TO_ID.inverse();

    private static final ConcurrentHashMap<Class<? extends IPacket<?>>, List<PacketHandler>> TYPED_LISTENERS = new ConcurrentHashMap<>();
    private static final Map<Object, List<PacketHandler>> LISTENERS_BY_TARGET = new MapMaker().weakKeys().makeMap();
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();

    public static void init() {
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(C2SPacket.class, ConnectionProtocol.PLAY);
        ConnectionProtocol.PROTOCOL_BY_PACKET.put(S2CPacket.class, ConnectionProtocol.PLAY);

        List<Class<? extends IPacket<?>>> sortedPackets = new ArrayList<>(REGISTERED_PACKET_TYPES);
        sortedPackets.sort(Comparator.comparing(Class::getName));

        lock.writeLock().lock();
        try {
            CLASS_TO_ID.clear();
            for (Class<? extends IPacket<?>> packetClass : sortedPackets) {
                CLASS_TO_ID.put(packetClass, CLASS_TO_ID.size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void registerPacketListener(@NotNull Class<?> targetClass) {
        registerPacketListenerInternal(targetClass, null);
    }

    public static void registerPacketListener(@NotNull Object targetInstance) {
        registerPacketListenerInternal(targetInstance.getClass(), targetInstance);
    }

    public static void registerPacketListener(@NotNull PacketHandler packetHandler) {
        lock.writeLock().lock();
        try {
            LISTENERS_BY_TARGET.computeIfAbsent(packetHandler.getPacketType(), o -> new ArrayList<>()).add(packetHandler);
            TYPED_LISTENERS.computeIfAbsent(packetHandler.getPacketType(), k -> new ArrayList<>()).add(packetHandler);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static void registerPacketListenerInternal(@NotNull Class<?> clazz, @Nullable Object instance) {
        List<PacketHandler> generatedHandlers = new ArrayList<>();
        boolean foundAnnotation = false;

        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(ClassPacketHandler.class)) continue;
            foundAnnotation = true;

            if (!Modifier.isPublic(method.getModifiers())) {
                AcademyCraft.LOGGER.error("Skipping method {} in {}: method is not public", method.getName(), clazz.getName());
                continue;
            }

            if (method.getParameterCount() != 1) {
                AcademyCraft.LOGGER.error("Skipping method {} in {}: method must have exactly one parameter", method.getName(), clazz.getName());
                continue;
            }

            if (!IPacket.class.isAssignableFrom(method.getParameterTypes()[0])) {
                AcademyCraft.LOGGER.error("Skipping method {} in {}: parameter type {} does not implement IPacket<?>", method.getName(), clazz.getName(), method.getParameterTypes()[0].getName());
                continue;
            }

            PacketHandler handler = (instance == null)
                    ? PacketHandlerFactory.createStatic(method)
                    : PacketHandlerFactory.createInstance(method, instance);
            generatedHandlers.add(handler);
        }

        if (!generatedHandlers.isEmpty()) {
            lock.writeLock().lock();
            try {
                Object key = (instance == null) ? clazz : instance;
                LISTENERS_BY_TARGET.put(key, List.copyOf(generatedHandlers));

                for (PacketHandler handler : generatedHandlers) {
                    TYPED_LISTENERS.computeIfAbsent(handler.getPacketType(), k -> new ArrayList<>()).add(handler);
                }
            } finally {
                lock.writeLock().unlock();
            }
        } else if (foundAnnotation) {
            AcademyCraft.LOGGER.warn("No valid packet handlers generated for class {} despite annotations present.", clazz.getName());
        }
    }

    public static void unregisterPacketListener(@NotNull Class<?> targetClass) {
        unregisterPacketListenerInternal(targetClass);
    }

    public static void unregisterPacketListener(@NotNull Object targetInstance) {
        unregisterPacketListenerInternal(targetInstance);
    }

    private static void unregisterPacketListenerInternal(@NotNull Object keyToRemove) {
        lock.writeLock().lock();
        try {
            List<PacketHandler> handlersToRemove = LISTENERS_BY_TARGET.remove(keyToRemove);
            if (handlersToRemove != null) {
                for (PacketHandler handler : handlersToRemove) {
                    List<PacketHandler> typedList = TYPED_LISTENERS.get(handler.getPacketType());
                    if (typedList != null) {
                        typedList.remove(handler);
                        if (typedList.isEmpty()) {
                            TYPED_LISTENERS.remove(handler.getPacketType());
                        }
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void registerPacketName(String packetName) {
        lock.writeLock().lock();
        try {
            if (!NAME_TO_ID.containsKey(packetName)) {
                NAME_TO_ID.put(packetName, NAME_TO_ID.size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static <T extends IPacket<?>> void registerPacketType(Class<T> packetClass) {
        REGISTERED_PACKET_TYPES.add(packetClass);
    }

    public static int getPacketIdByName(String packetName) {
        lock.readLock().lock();
        try {
            return NAME_TO_ID.getOrDefault(packetName, -1);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static <T extends IPacket<?>> int getPacketIdByType(Class<T> packetClass) {
        lock.readLock().lock();
        try {
            return CLASS_TO_ID.getOrDefault(packetClass, -1);
        } finally {
            lock.readLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends IPacket<?>> Class<T> getClassById(int id) {
        lock.readLock().lock();
        try {
            return (Class<T>) ID_TO_CLASS.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nullable
    public static String getNameById(int id) {
        lock.readLock().lock();
        try {
            return ID_TO_NAME.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static <T extends IPacket<?>> void dispatchPacket(T packet) {
        List<PacketHandler> handlers = null;
        lock.readLock().lock();
        try {
            List<PacketHandler> typedList = TYPED_LISTENERS.get(packet.getClass());
            if (typedList != null && !typedList.isEmpty()) {
                handlers = Lists.newArrayList(typedList);
            }
        } finally {
            lock.readLock().unlock();
        }

        if (handlers != null) {
            for (PacketHandler handler : handlers) {
                try {
                    handler.handlePacket(packet);
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error("Exception dispatching packet {} to handler {}: {}", packet.getClass().getSimpleName(), handler.getClass().getName(), e.getMessage(), e);
                }
            }
        }
    }
}