package org.academy.api.common.network;

import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import org.academy.AcademyCraft;
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

public abstract class AbstractNetworkManager {
    protected final NetworkSystem networkSystem;
    protected final ConcurrentHashMap<Class<? extends IPacket<?>>, List<IPacketListener>> typedListeners;
    protected final Map<Object, List<IPacketListener>> listenersByTarget;
    protected final ReadWriteLock lock;

    protected AbstractNetworkManager(NetworkSystem networkSystem) {
        this.networkSystem = networkSystem;
        this.typedListeners = new ConcurrentHashMap<>();
        this.listenersByTarget = new MapMaker().weakKeys().makeMap();
        this.lock = new ReentrantReadWriteLock();
    }

    public void clear() {
        this.lock.writeLock().lock();
        try {
            this.typedListeners.clear();
            this.listenersByTarget.clear();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void registerPacketListener(@NotNull Class<?> targetClass) {
        registerPacketListenerInternal(targetClass, null);
    }

    public void registerPacketListener(@NotNull Object targetInstance) {
        registerPacketListenerInternal(targetInstance.getClass(), targetInstance);
    }

    public void registerPacketListener(@NotNull IPacketListener iPacketListener) {
        this.lock.writeLock().lock();
        try {
            this.listenersByTarget.computeIfAbsent(iPacketListener.getPacketType(), o -> new ArrayList<>()).add(iPacketListener);
            this.typedListeners.computeIfAbsent(iPacketListener.getPacketType(), k -> new ArrayList<>()).add(iPacketListener);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private void registerPacketListenerInternal(@NotNull Class<?> clazz, @Nullable Object instance) {
        List<IPacketListener> generatedHandlers = NetworkSystem.findPacketListeners(clazz, instance);

        if (!generatedHandlers.isEmpty()) {
            this.lock.writeLock().lock();
            try {
                Object key = (instance == null) ? clazz : instance;
                this.listenersByTarget.put(key, List.copyOf(generatedHandlers));

                for (IPacketListener handler : generatedHandlers) {
                    this.typedListeners.computeIfAbsent(handler.getPacketType(), k -> new ArrayList<>()).add(handler);
                }
            } finally {
                this.lock.writeLock().unlock();
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
        this.lock.writeLock().lock();
        try {
            List<IPacketListener> handlersToRemove = this.listenersByTarget.remove(keyToRemove);
            if (handlersToRemove != null) {
                for (IPacketListener handler : handlersToRemove) {
                    List<IPacketListener> typedList = this.typedListeners.get(handler.getPacketType());
                    if (typedList != null) {
                        typedList.remove(handler);
                        if (typedList.isEmpty()) {
                            this.typedListeners.remove(handler.getPacketType());
                        }
                    }
                }
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public <T extends IPacket<?>> void dispatchPacket(T packet) {
        List<IPacketListener> handlers = null;
        this.lock.readLock().lock();
        try {
            List<IPacketListener> typedList = this.typedListeners.get(packet.getClass());
            if (typedList != null && !typedList.isEmpty()) {
                handlers = Lists.newArrayList(typedList);
            }
        } finally {
            this.lock.readLock().unlock();
        }

        if (handlers != null) {
            for (IPacketListener handler : handlers) {
                try {
                    handler.handlePacket(packet);
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error("Exception dispatching packet {} to handler {}: {}", packet.getClass().getSimpleName(), handler.getClass().getName(), e.getMessage(), e);
                }
            }
        }
    }

    public NetworkSystem getNetworkSystem() {
        return networkSystem;
    }
}