package org.academy.api.common.network.future;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.academy.api.common.asm.InstanceCreator;
import org.academy.api.common.asm.InstanceCreatorFactory;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.packet.FutureRequestPacket;
import org.academy.api.common.network.packet.FutureResponsePacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FutureManager {
    private static final BiMap<Class<? extends IPayload>, Integer> PAYLOAD_CLASS_TO_ID = HashBiMap.create();
    private static final Map<Integer, Class<? extends IPayload>> ID_TO_PAYLOAD_CLASS = new ConcurrentHashMap<>();
    private static final Map<Class<? extends IPayload>, InstanceCreator<? extends IPayload>> PAYLOAD_CREATORS = new ConcurrentHashMap<>();
    private static final AtomicInteger nextPayloadId = new AtomicInteger(0);

    private FutureManager() {
    }

    public static void init() {
        NetworkSystem.registerPacketType(FutureRequestPacket.class);
        NetworkSystem.registerPacketType(FutureResponsePacket.class);
    }

    public static <T extends IPayload> void registerPayloadType(Class<T> payloadClass) {
        if (!PAYLOAD_CLASS_TO_ID.containsKey(payloadClass)) {
            int id = nextPayloadId.getAndIncrement();
            PAYLOAD_CLASS_TO_ID.put(payloadClass, id);
            ID_TO_PAYLOAD_CLASS.put(id, payloadClass);
            PAYLOAD_CREATORS.put(payloadClass, InstanceCreatorFactory.createInstanceCreator(payloadClass));
        }
    }

    public static int getPayloadId(Class<? extends IPayload> payloadClass) {
        Integer id = PAYLOAD_CLASS_TO_ID.get(payloadClass);
        if (id == null) {
            throw new IllegalArgumentException("Payload type not registered: " + payloadClass.getName());
        }
        return id;
    }

    public static Class<? extends IPayload> getPayloadClass(int id) {
        return ID_TO_PAYLOAD_CLASS.get(id);
    }

    @SuppressWarnings("unchecked")
    public static <T extends IPayload> InstanceCreator<T> getPayloadCreator(int payloadId) {
        Class<? extends IPayload> payloadClass = getPayloadClass(payloadId);
        return (InstanceCreator<T>) PAYLOAD_CREATORS.get(payloadClass);
    }
}