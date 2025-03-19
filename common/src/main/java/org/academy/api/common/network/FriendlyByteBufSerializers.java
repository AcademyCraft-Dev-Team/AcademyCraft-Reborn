package org.academy.api.common.network;

import org.academy.api.common.ability.AbilityCategory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
public class FriendlyByteBufSerializers {
    private static final Map<Class<?>, FriendlyByteBufSerializer<?>> FRIENDLY_BYTE_BUF_SERIALIZER_MAP = new ConcurrentHashMap<>();
    public static final FriendlyByteBufSerializer<AbilityCategory> ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(AbilityCategory.class, (buffer, value) -> buffer.writeUtf(value.name));

    public static <T> FriendlyByteBufSerializer<T> registerSerializer(Class<T> clazz, FriendlyByteBufSerializer<T> serializer) {
        FRIENDLY_BYTE_BUF_SERIALIZER_MAP.put(clazz, serializer);
        return serializer;
    }

    /**
     *  一般情况下不用调用，在不确定 Class 时才调用
     */
    public static <T> Optional<FriendlyByteBufDeserializer<T>> getSerializer(Class<T> clazz) {
        return Optional.ofNullable((FriendlyByteBufDeserializer<T>) FRIENDLY_BYTE_BUF_SERIALIZER_MAP.get(clazz));
    }

    private FriendlyByteBufSerializers() {
    }
}
