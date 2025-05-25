package org.academy.api.server.network;

import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
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

public class NetworkSystemServer {
    private final NetworkSystem networkSystem;
    private final ConcurrentHashMap<Class<? extends IPacket<?>>, List<IPacketListener>> serverTypedListeners;
    private final Map<Object, List<IPacketListener>> serverListenersByTarget;
    private final ReadWriteLock serverLock;

    public NetworkSystemServer(NetworkSystem networkSystem) {
        this.networkSystem = networkSystem;
        this.serverTypedListeners = new ConcurrentHashMap<>();
        this.serverListenersByTarget = new MapMaker().weakKeys().makeMap();
        this.serverLock = new ReentrantReadWriteLock();
    }

    public void init() {
        this.serverLock.writeLock().lock();
        try {
            this.serverTypedListeners.clear();
            this.serverListenersByTarget.clear();
        } finally {
            this.serverLock.writeLock().unlock();
        }
    }

    public void registerPacketListener(@NotNull Class<?> targetClass) {
        registerPacketListenerInternal(targetClass, null);
    }

    public void registerPacketListener(@NotNull Object targetInstance) {
        registerPacketListenerInternal(targetInstance.getClass(), targetInstance);
    }

    public void registerPacketListener(@NotNull IPacketListener iPacketListener) {
        this.serverLock.writeLock().lock();
        try {
            this.serverListenersByTarget.computeIfAbsent(iPacketListener.getPacketType(), o -> new ArrayList<>()).add(iPacketListener);
            this.serverTypedListeners.computeIfAbsent(iPacketListener.getPacketType(), k -> new ArrayList<>()).add(iPacketListener);
        } finally {
            this.serverLock.writeLock().unlock();
        }
    }

    private void registerPacketListenerInternal(@NotNull Class<?> clazz, @Nullable Object instance) {
        List<IPacketListener> generatedHandlers = NetworkSystem.findPacketListeners(clazz, instance);

        if (!generatedHandlers.isEmpty()) {
            this.serverLock.writeLock().lock();
            try {
                Object key = (instance == null) ? clazz : instance;
                this.serverListenersByTarget.put(key, List.copyOf(generatedHandlers));

                for (IPacketListener handler : generatedHandlers) {
                    this.serverTypedListeners.computeIfAbsent(handler.getPacketType(), k -> new ArrayList<>()).add(handler);
                }
            } finally {
                this.serverLock.writeLock().unlock();
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
        this.serverLock.writeLock().lock();
        try {
            List<IPacketListener> handlersToRemove = this.serverListenersByTarget.remove(keyToRemove);
            if (handlersToRemove != null) {
                for (IPacketListener handler : handlersToRemove) {
                    List<IPacketListener> typedList = this.serverTypedListeners.get(handler.getPacketType());
                    if (typedList != null) {
                        typedList.remove(handler);
                        if (typedList.isEmpty()) {
                            this.serverTypedListeners.remove(handler.getPacketType());
                        }
                    }
                }
            }
        } finally {
            this.serverLock.writeLock().unlock();
        }
    }

    public <T extends IPacket<?>> void dispatchServerPacket(T packet) {
        List<IPacketListener> handlers = null;
        this.serverLock.readLock().lock();
        try {
            List<IPacketListener> typedList = this.serverTypedListeners.get(packet.getClass());
            if (typedList != null && !typedList.isEmpty()) {
                handlers = Lists.newArrayList(typedList);
            }
        } finally {
            this.serverLock.readLock().unlock();
        }

        if (handlers != null) {
            for (IPacketListener handler : handlers) {
                try {
                    handler.handlePacket(packet);
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error("Exception dispatching server packet {} to handler {}: {}", packet.getClass().getSimpleName(), handler.getClass().getName(), e.getMessage(), e);
                }
            }
        }
    }

    public NetworkSystem getNetworkSystem() {
        return networkSystem;
    }
}