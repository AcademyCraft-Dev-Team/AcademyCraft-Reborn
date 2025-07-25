package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public class FriendlyByteBufDeserializers {
    public static final BiMap<FriendlyByteBufDeserializer, Integer> DESERIALIZER_IDS = HashBiMap.create();
    private static final Map<Class<?>, FriendlyByteBufDeserializer<?>> DESERIALIZER_MAP = new ConcurrentHashMap<>();
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
    public static final FriendlyByteBufDeserializer<BlockPos> BLOCK_POS_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(BlockPos.class, buffer -> buffer.readBlockPos());
    public static final FriendlyByteBufDeserializer<UUID> UUID_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(UUID.class, buffer -> buffer.readUUID());
    public static final FriendlyByteBufDeserializer<Vector3f> VECTOR_3_F_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Vector3f.class, buffer -> buffer.readVector3f());
    public static final FriendlyByteBufDeserializer<Tag> COMPOUND_TAG_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Tag.class, buffer -> buffer.readNbt());
    public static final FriendlyByteBufDeserializer<ArrayList> ARRAY_LIST_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(ArrayList.class, buffer -> {
        var nonEmpty = buffer.readBoolean();
        if (nonEmpty) {
            var elementTypeId = buffer.readVarInt();
            var elementDeserializer = getRequiredDeserializer(elementTypeId);
            var size = buffer.readVarInt();
            var list = new ArrayList(size);
            for (var i = 0; i < size; i++) {
                list.add(elementDeserializer.deserialize(buffer));
            }
            return list;
        } else {
            return new ArrayList<>();
        }
    });
    public static final FriendlyByteBufDeserializer<HashMap> MAP_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(HashMap.class, buffer -> {
        var size = buffer.readVarInt();
        var isEmpty = buffer.readBoolean();
        var map = new HashMap<>(size);
        if (!isEmpty) {
            var keyTypeId = buffer.readVarInt();
            var valueTypeId = buffer.readVarInt();
            var keyDeserializer = getRequiredDeserializer(keyTypeId);
            var valueDeserializer = getRequiredDeserializer(valueTypeId);

            for (var i = 0; i < size; i++) {
                var key = keyDeserializer.deserialize(buffer);
                var value = valueDeserializer.deserialize(buffer);
                map.put(key, value);
            }
        }
        return map;
    });
    public static final FriendlyByteBufDeserializer<HashSet> HASH_SET_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(HashSet.class, buffer -> {
        var nonEmpty = buffer.readBoolean();
        if (nonEmpty) {
            var elementTypeId = buffer.readVarInt();
            var elementDeserializer = getRequiredDeserializer(elementTypeId);
            var size = buffer.readVarInt();
            var set = new HashSet(size);
            for (var i = 0; i < size; i++) {
                set.add(elementDeserializer.deserialize(buffer));
            }
            return set;
        } else {
            return new HashSet<>();
        }
    });
    public static final FriendlyByteBufDeserializer<Class> CLASS_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Class.class, buffer -> {
        try {
            return Class.forName(buffer.readUtf());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    });
    public static final FriendlyByteBufDeserializer<ImmutablePair> PAIR_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(ImmutablePair.class, buffer -> {
        var leftTypeId = buffer.readVarInt();
        var rightTypeId = buffer.readVarInt();
        var leftDeserializer = getRequiredDeserializer(leftTypeId);
        var rightDeserializer = getRequiredDeserializer(rightTypeId);

        var left = leftDeserializer.deserialize(buffer);
        var right = rightDeserializer.deserialize(buffer);

        return new ImmutablePair<>(left, right);
    });

    public static <T, C extends Collection<T>> FriendlyByteBufDeserializer<C> getCollectionFriendlyByteBufDeserializer(Class<T> elementType, Supplier<C> collectionFactory) {
        return buffer -> {
            var collection = collectionFactory.get();
            var size = buffer.readVarInt();
            var elementDeserializer = (FriendlyByteBufDeserializer<T>) getRequiredDeserializer(elementType);
            for (var i = 0; i < size; i++) {
                collection.add(elementDeserializer.deserialize(buffer));
            }
            return collection;
        };
    }

    public static <T> FriendlyByteBufDeserializer<T> registerDeserializer(
            Class<T> clazz, FriendlyByteBufDeserializer<T> deserializer
    ) {
        DESERIALIZER_IDS.put(deserializer, DESERIALIZER_IDS.size());
        DESERIALIZER_MAP.put(clazz, deserializer);
        return deserializer;
    }

    public static int getDeserializerId(FriendlyByteBufDeserializer<?> serializer) {
        return DESERIALIZER_IDS.get(serializer);
    }

    public static int getDeserializerId(Class<?> clazz) {
        return DESERIALIZER_IDS.get(getRequiredDeserializer(clazz));
    }

    @Nullable
    public static <T> FriendlyByteBufDeserializer<T> getDeserializer(int id) {
        return (FriendlyByteBufDeserializer<T>) DESERIALIZER_IDS.inverse().get(id);
    }

    public static <T> FriendlyByteBufDeserializer<T> getRequiredDeserializer(int id) {
        var deserializer = getDeserializer(id);
        if (deserializer == null) {
            throw new RuntimeException("No deserializer found for id " + id);
        } else {
            return (FriendlyByteBufDeserializer<T>) deserializer;
        }
    }

    @Nullable
    public static <T> FriendlyByteBufDeserializer<T> getDeserializer(Class<?> clazz) {
        return (FriendlyByteBufDeserializer<T>) DESERIALIZER_MAP.get(clazz);
    }

    public static <T> FriendlyByteBufDeserializer<T> getRequiredDeserializer(Class<?> clazz) {
        var deserializer = getDeserializer(clazz);
        if (deserializer == null) {
            for (var entry : DESERIALIZER_MAP.entrySet()) {
                if (entry.getKey().isAssignableFrom(clazz)) {
                    return (FriendlyByteBufDeserializer<T>) entry.getValue();
                }
            }
            throw new NullPointerException("Deserializer for " + clazz.getCanonicalName() + " was null and no assignable deserializer found.");
        } else {
            return (FriendlyByteBufDeserializer<T>) deserializer;
        }
    }
}