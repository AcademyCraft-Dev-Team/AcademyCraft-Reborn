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
        this.payloadClassToId = HashBiMap.create();
        this.idToPayloadClass = new ConcurrentHashMap<>();
        this.payloadFactories = new ConcurrentHashMap<>();
        this.nextPayloadId = new AtomicInteger(0);
    }

    public <T extends IPayload> void registerPayloadType(Class<T> payloadClass, Function<? extends PacketListener, T> factory) {
        if (!this.payloadClassToId.containsKey(payloadClass)) {
            int id = this.nextPayloadId.getAndIncrement();
            this.payloadClassToId.put(payloadClass, id);
            this.idToPayloadClass.put(id, payloadClass);
            this.payloadFactories.put(payloadClass, factory);
        }
    }

    public int getPayloadId(Class<? extends IPayload> payloadClass) {
        return this.payloadClassToId.get(payloadClass);
    }

    public Class<? extends IPayload> getPayloadClass(int id) {
        return this.idToPayloadClass.get(id);
    }

    @SuppressWarnings("unchecked")
    public <T extends IPayload> Function<PacketListener, T> getPayloadFactory(int payloadId) {
        Class<? extends IPayload> payloadClass = getPayloadClass(payloadId);
        if (payloadClass == null) {
            return null;
        }
        return (Function<PacketListener, T>) this.payloadFactories.get(payloadClass);
    }
}