package org.academy.api.common.network.future;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.network.PacketListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class FutureManager {
    private final BiMap<Class<? extends IPayload>, Integer> payloadClassToId;
    private final Map<Integer, Class<? extends IPayload>> idToPayloadClass;
    private final Map<Class<? extends IPayload>, Function<? extends PacketListener, ? extends IPayload>> payloadFactories;
    private final AtomicInteger nextPayloadId;

    public FutureManager() {
        payloadClassToId = HashBiMap.create();
        idToPayloadClass = new ConcurrentHashMap<>();
        payloadFactories = new ConcurrentHashMap<>();
        nextPayloadId = new AtomicInteger(0);
    }

    public <T extends IPayload> void registerPayloadType(Class<T> payloadClass, Function<? extends PacketListener, T> factory) {
        if (!payloadClassToId.containsKey(payloadClass)) {
            var id = nextPayloadId.getAndIncrement();
            payloadClassToId.put(payloadClass, id);
            idToPayloadClass.put(id, payloadClass);
            payloadFactories.put(payloadClass, factory);
        }
    }

    public int getPayloadId(Class<? extends IPayload> payloadClass) {
        if (payloadClassToId.containsKey(payloadClass)) {
            return payloadClassToId.get(payloadClass);
        } else {
            throw new IllegalStateException("PayloadType " + payloadClass + " not registered.");
        }
    }

    public Class<? extends IPayload> getPayloadClass(int id) {
        return idToPayloadClass.get(id);
    }

    @SuppressWarnings("unchecked")
    public <T extends IPayload, L extends PacketListener> Function<L, T> getPayloadFactory(int payloadId) {
        var payloadClass = getPayloadClass(payloadId);
        if (payloadClass == null) {
            return null;
        }
        return (Function<L, T>) payloadFactories.get(payloadClass);
    }
}