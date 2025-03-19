package org.academy.api.common.network;

import org.academy.AbilitySystem;
import org.academy.api.common.ability.AbilityCategory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
public class FriendlyByteBufDeserializers {
    private static final Map<Class<?>, FriendlyByteBufDeserializer<?>> FRIENDLY_BYTE_BUF_DESERIALIZER_MAP = new ConcurrentHashMap<>();
    public static final FriendlyByteBufDeserializer<AbilityCategory> ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(AbilityCategory.class, buffer -> AbilitySystem.ABILITY_CATEGORY_MAP.get(buffer.readUtf()));

    public static <T> FriendlyByteBufDeserializer<T> registerDeserializer(Class<T> clazz, FriendlyByteBufDeserializer<T> deserializer) {
        FRIENDLY_BYTE_BUF_DESERIALIZER_MAP.put(clazz, deserializer);
        return deserializer;
    }

    /**
     *  一般情况下不用调用，在不确定 Class 时才调用
     */
    public static <T> Optional<FriendlyByteBufDeserializer<T>> getDeserializer(Class<T> clazz) {
        return Optional.ofNullable((FriendlyByteBufDeserializer<T>) FRIENDLY_BYTE_BUF_DESERIALIZER_MAP.get(clazz));
    }

    private FriendlyByteBufDeserializers() {
    }
}