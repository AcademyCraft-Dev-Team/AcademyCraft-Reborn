package org.academy.api.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.academy.AbilitySystem;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FriendlyByteBufDeserializers {
    private static final Map<Class<?>, FriendlyByteBufDeserializer<?>> FRIENDLY_BYTE_BUF_DESERIALIZER_MAP = new ConcurrentHashMap<>();
    public static final FriendlyByteBufDeserializer<String> STRING_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(String.class, FriendlyByteBuf::readUtf);
    public static final FriendlyByteBufDeserializer<Integer> INTEGER_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Integer.class, FriendlyByteBuf::readVarInt);
    public static final FriendlyByteBufDeserializer<Long> LONG_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Long.class, FriendlyByteBuf::readVarLong);
    public static final FriendlyByteBufDeserializer<Float> FLOAT_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Float.class, FriendlyByteBuf::readFloat);
    public static final FriendlyByteBufDeserializer<Double> DOUBLE_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Double.class, FriendlyByteBuf::readDouble);
    public static final FriendlyByteBufDeserializer<Boolean> BOOLEAN_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Boolean.class, FriendlyByteBuf::readBoolean);
    public static final FriendlyByteBufDeserializer<Byte> BYTE_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Byte.class, FriendlyByteBuf::readByte);
    public static final FriendlyByteBufDeserializer<Short> SHORT_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Short.class, FriendlyByteBuf::readShort);
    public static final FriendlyByteBufDeserializer<Character> CHAR_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Character.class, FriendlyByteBuf::readChar);
    public static final FriendlyByteBufDeserializer<AbilityCategory> ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(AbilityCategory.class, buffer -> AbilitySystem.ABILITY_CATEGORY_MAP.get(buffer.readUtf()));
    public static final FriendlyByteBufDeserializer<Skill> SKILL_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Skill.class, buffer -> AbilitySystem.SKILL_MAP.get(buffer.readUtf()));
    public static final FriendlyByteBufDeserializer<BlockPos> BLOCK_POS_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(BlockPos.class, FriendlyByteBuf::readBlockPos);
    public static final FriendlyByteBufDeserializer<UUID> UUID_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(UUID.class, FriendlyByteBuf::readUUID);
    public static final FriendlyByteBufDeserializer<Component> COMPONENT_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Component.class, FriendlyByteBuf::readComponent);
    public static final FriendlyByteBufDeserializer<Vector3f> VECTOR_3_F_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Vector3f.class, FriendlyByteBuf::readVector3f);
    public static final FriendlyByteBufDeserializer<CompoundTag> COMPOUND_TAG_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(CompoundTag.class, FriendlyByteBuf::readNbt);
    public static final FriendlyByteBufDeserializer<List> LIST_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(List.class, buffer -> {
        int size = buffer.readVarInt();
        boolean isEmpty = buffer.readBoolean();
        List<Object> list = new ArrayList<>(size);
        if (!isEmpty) {
            Class<?> clazz;
            try {
                clazz = Class.forName(buffer.readUtf());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("The data transmission may be corrupted." + e);
            }
            for (int i = 0; i < size; i++) {
                FriendlyByteBufDeserializer serializer = getRequiredDeserializer(clazz);
                list.add(serializer.deserialize(buffer));
            }
        }
        return list;
    });
    public static final FriendlyByteBufDeserializer<ArrayList> ARRAY_LIST_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(ArrayList.class, (FriendlyByteBufDeserializer<ArrayList>) FRIENDLY_BYTE_BUF_DESERIALIZER_MAP.get(List.class));
    public static final FriendlyByteBufDeserializer<Set> SET_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Set.class, buffer -> {
        int size = buffer.readVarInt();
        boolean isEmpty = buffer.readBoolean();
        Set<Object> set = new HashSet<>(size);
        if (!isEmpty) {
            Class<?> clazz;
            try {
                clazz = Class.forName(buffer.readUtf());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("The data transmission may be corrupted." + e);
            }
            for (int i = 0; i < size; i++) {
                FriendlyByteBufDeserializer serializer = getRequiredDeserializer(clazz);
                set.add(serializer.deserialize(buffer));
            }
        }
        return set;
    });
    public static final FriendlyByteBufDeserializer<Map> MAP_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Map.class, buffer -> {
        int size = buffer.readVarInt();
        boolean isEmpty = buffer.readBoolean();
        Map<Object, Object> map = new HashMap<>(size);
        if (!isEmpty) {
            Class<?> keyClass;
            Class<?> valueClass;
            try {
                keyClass = Class.forName(buffer.readUtf());
                valueClass = Class.forName(buffer.readUtf());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("The data transmission may be corrupted." + e);
            }
            for (int i = 0; i < size; i++) {
                FriendlyByteBufDeserializer keyDeserializer = getRequiredDeserializer(keyClass);
                FriendlyByteBufDeserializer valueDeserializer = getRequiredDeserializer(valueClass);
                Object key = keyDeserializer.deserialize(buffer);
                Object value = valueDeserializer.deserialize(buffer);
                map.put(key, value);
            }
        }
        return map;
    });

    public static <T> FriendlyByteBufDeserializer<T> registerDeserializer(Class<T> clazz, FriendlyByteBufDeserializer<T> deserializer) {
        FRIENDLY_BYTE_BUF_DESERIALIZER_MAP.put(clazz, deserializer);
        return deserializer;
    }

    /**
     * 一般情况下不用调用，在不确定 Class 时才调用
     */
    @Nullable
    public static <T> FriendlyByteBufDeserializer<T> getDeserializer(Class<T> clazz) {
        return (FriendlyByteBufDeserializer<T>) FRIENDLY_BYTE_BUF_DESERIALIZER_MAP.get(clazz);
    }

    public static <T> FriendlyByteBufDeserializer<T> getRequiredDeserializer(Class<T> clazz) {
        FriendlyByteBufDeserializer<T> serializer = getDeserializer(clazz);
        if (serializer == null) {
            throw new NullPointerException("Deserializer for " + clazz.getCanonicalName() + " was null");
        }
        return serializer;
    }


    private FriendlyByteBufDeserializers() {
    }
}