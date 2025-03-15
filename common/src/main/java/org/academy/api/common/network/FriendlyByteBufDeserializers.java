package org.academy.api.common.network;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
public class FriendlyByteBufDeserializers {
    private static final Map<Class<?>, FriendlyByteBufDeserializer<?>> FRIENDLY_BYTE_BUF_DESERIALIZER_MAP = new ConcurrentHashMap<>();

    public static <T> void registerDeserializer(Class<T> clazz, FriendlyByteBufDeserializer<T> deserializer) {
        FRIENDLY_BYTE_BUF_DESERIALIZER_MAP.put(clazz, deserializer);
    }

    public static <T> Optional<FriendlyByteBufDeserializer<T>> getDeserializer(Class<T> clazz) {
        return Optional.ofNullable((FriendlyByteBufDeserializer<T>) FRIENDLY_BYTE_BUF_DESERIALIZER_MAP.get(clazz));
    }

    private FriendlyByteBufDeserializers() {
    }
}