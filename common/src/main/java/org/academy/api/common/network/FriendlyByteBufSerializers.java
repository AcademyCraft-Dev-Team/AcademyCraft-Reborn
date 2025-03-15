package org.academy.api.common.network;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
public class FriendlyByteBufSerializers {
    private static final Map<Class<?>, FriendlyByteBufSerializer<?>> FRIENDLY_BYTE_BUF_SERIALIZER_MAP = new ConcurrentHashMap<>();

    public static <T> void registerSerializer(Class<T> clazz, FriendlyByteBufSerializer<T> serializer) {
        FRIENDLY_BYTE_BUF_SERIALIZER_MAP.put(clazz, serializer);
    }

    public static <T> Optional<FriendlyByteBufDeserializer<T>> getSerializer(Class<T> clazz) {
        return Optional.ofNullable((FriendlyByteBufDeserializer<T>) FRIENDLY_BYTE_BUF_SERIALIZER_MAP.get(clazz));
    }

    private FriendlyByteBufSerializers() {
    }
}
