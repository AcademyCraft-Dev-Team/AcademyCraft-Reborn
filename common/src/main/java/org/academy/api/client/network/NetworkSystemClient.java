package org.academy.api.client.network;

import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.academy.AcademyCraft;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.asm.IPacketListener;
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
    private final NetworkSystem networkSystem;
    private final ConcurrentHashMap<Class<? extends IPacket<?>>, List<IPacketListener>> clientTypedListeners;
    private final Map<Object, List<IPacketListener>> clientListenersByTarget;
    private final ReadWriteLock clientLock;

    public NetworkSystemClient(NetworkSystem networkSystem) {
        this.networkSystem = networkSystem;
        this.clientTypedListeners = new ConcurrentHashMap<>();
        this.clientListenersByTarget = new MapMaker().weakKeys().makeMap();
        this.clientLock = new ReentrantReadWriteLock();
    }

    public void clear() {
        this.clientLock.writeLock().lock();
        try {
            this.clientTypedListeners.clear();
            this.clientListenersByTarget.clear();
        } finally {
            this.clientLock.writeLock().unlock();
        }
    }

    public void registerPacketListener(@NotNull Class<?> targetClass) {
        registerPacketListenerInternal(targetClass, null);
    }

    public void registerPacketListener(@NotNull Object targetInstance) {
        registerPacketListenerInternal(targetInstance.getClass(), targetInstance);
    }

    public void registerPacketListener(@NotNull IPacketListener iPacketListener) {
        this.clientLock.writeLock().lock();
        try {
            this.clientListenersByTarget.computeIfAbsent(iPacketListener.getPacketType(), o -> new ArrayList<>()).add(iPacketListener);
            this.clientTypedListeners.computeIfAbsent(iPacketListener.getPacketType(), k -> new ArrayList<>()).add(iPacketListener);
        } finally {
            this.clientLock.writeLock().unlock();
        }
    }

    private void registerPacketListenerInternal(@NotNull Class<?> clazz, @Nullable Object instance) {
        List<IPacketListener> generatedHandlers = NetworkSystem.findPacketListeners(clazz, instance);

        if (!generatedHandlers.isEmpty()) {
            this.clientLock.writeLock().lock();
            try {
                Object key = (instance == null) ? clazz : instance;
                this.clientListenersByTarget.put(key, List.copyOf(generatedHandlers));

                for (IPacketListener handler : generatedHandlers) {
                    this.clientTypedListeners.computeIfAbsent(handler.getPacketType(), k -> new ArrayList<>()).add(handler);
                }
            } finally {
                this.clientLock.writeLock().unlock();
            }
        }
    }

    public void unregisterPacketListener(@NotNull Class<?> targetClass) {
        unregisterPacketListenerInternal(targetClass);
    }

    public void unregisterPacketListener(@NotNull Object targetInstance) {
        unregisterPacketListenerInternal(targetInstance);
    }

    private void unregisterPacketListenerInternal(@NotNull Object keyToRemove) {
        this.clientLock.writeLock().lock();
        try {
            List<IPacketListener> handlersToRemove = this.clientListenersByTarget.remove(keyToRemove);
            if (handlersToRemove != null) {
                for (IPacketListener handler : handlersToRemove) {
                    List<IPacketListener> typedList = this.clientTypedListeners.get(handler.getPacketType());
                    if (typedList != null) {
                        typedList.remove(handler);
                        if (typedList.isEmpty()) {
                            this.clientTypedListeners.remove(handler.getPacketType());
                        }
                    }
                }
            }
        } finally {
            this.clientLock.writeLock().unlock();
        }
    }

    public <T extends IPacket<?>> void dispatchClientPacket(T packet) {
        List<IPacketListener> handlers = null;
        this.clientLock.readLock().lock();
        try {
            List<IPacketListener> typedList = this.clientTypedListeners.get(packet.getClass());
            if (typedList != null && !typedList.isEmpty()) {
                handlers = Lists.newArrayList(typedList);
            }
        } finally {
            this.clientLock.readLock().unlock();
        }

        if (handlers != null) {
            for (IPacketListener handler : handlers) {
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

    public NetworkSystem getNetworkSystem() {
        return networkSystem;
    }
}