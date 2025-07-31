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

public final class NetworkManager {
    private final ConcurrentHashMap<Class<? extends IPacket<?>>, List<IPacketListener>> typedListeners;
    private final Map<Object, List<IPacketListener>> listenersByTarget;
    private final ReadWriteLock lock;

    public NetworkManager() {
        typedListeners = new ConcurrentHashMap<>();
        listenersByTarget = new MapMaker().weakKeys().makeMap();
        lock = new ReentrantReadWriteLock();
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            typedListeners.clear();
            listenersByTarget.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void registerPacketListener(@NotNull Class<?> targetClass) {
        registerPacketListenerInternal(targetClass, null);
    }

    public void registerPacketListener(@NotNull Object targetInstance) {
        registerPacketListenerInternal(targetInstance.getClass(), targetInstance);
    }

    public void registerPacketListener(@NotNull IPacketListener iPacketListener) {
        lock.writeLock().lock();
        try {
            listenersByTarget.computeIfAbsent(iPacketListener.getPacketType(), o -> new ArrayList<>()).add(iPacketListener);
            typedListeners.computeIfAbsent(iPacketListener.getPacketType(), k -> new ArrayList<>()).add(iPacketListener);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerPacketListenerInternal(@NotNull Class<?> clazz, @Nullable Object instance) {
        var generatedHandlers = NetworkSystem.findPacketListeners(clazz, instance);

        if (!generatedHandlers.isEmpty()) {
            lock.writeLock().lock();
            try {
                var key = (instance == null) ? clazz : instance;
                listenersByTarget.put(key, List.copyOf(generatedHandlers));

                for (var handler : generatedHandlers) {
                    typedListeners.computeIfAbsent(handler.getPacketType(), k -> new ArrayList<>()).add(handler);
                }
            } finally {
                lock.writeLock().unlock();
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
        lock.writeLock().lock();
        try {
            var handlersToRemove = listenersByTarget.remove(keyToRemove);
            if (handlersToRemove != null) {
                for (var handler : handlersToRemove) {
                    var typedList = typedListeners.get(handler.getPacketType());
                    if (typedList != null) {
                        typedList.remove(handler);
                        if (typedList.isEmpty()) {
                            typedListeners.remove(handler.getPacketType());
                        }
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public <T extends IPacket<?>> void dispatchPacket(T packet) {
        List<IPacketListener> handlers = null;
        lock.readLock().lock();
        try {
            var typedList = typedListeners.get(packet.getClass());
            if (typedList != null && !typedList.isEmpty()) {
                handlers = Lists.newArrayList(typedList);
            }
        } finally {
            lock.readLock().unlock();
        }

        if (handlers != null) {
            for (var handler : handlers) {
                try {
                    handler.handlePacket(packet);
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error("Exception dispatching packet {} to handler {}: {}", packet.getClass().getSimpleName(), handler.getClass().getName(), e.getMessage(), e);
                }
            }
        }
    }
}