package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.registries.Registries;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public class FBBDeserializers {
    public static final BiMap<FBBDeserializer, Integer> DESERIALIZER_IDS = HashBiMap.create();
    private static final Map<Class<?>, FBBDeserializer<?>> DESERIALIZER_MAP = new ConcurrentHashMap<>();
    public static final FBBDeserializer<String> STRING = registerDeserializer(String.class, FriendlyByteBuf::readUtf);
    public static final FBBDeserializer<Integer> INTEGER = registerDeserializer(Integer.class, FriendlyByteBuf::readVarInt);
    public static final FBBDeserializer<Long> LONG = registerDeserializer(Long.class, FriendlyByteBuf::readVarLong);
    public static final FBBDeserializer<Float> FLOAT = registerDeserializer(Float.class, FriendlyByteBuf::readFloat);
    public static final FBBDeserializer<Double> DOUBLE = registerDeserializer(Double.class, FriendlyByteBuf::readDouble);
    public static final FBBDeserializer<Boolean> BOOLEAN = registerDeserializer(Boolean.class, FriendlyByteBuf::readBoolean);
    public static final FBBDeserializer<Byte> BYTE = registerDeserializer(Byte.class, FriendlyByteBuf::readByte);
    public static final FBBDeserializer<Short> SHORT = registerDeserializer(Short.class, FriendlyByteBuf::readShort);
    public static final FBBDeserializer<Character> CHAR_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Character.class, FriendlyByteBuf::readChar);
    public static final FBBDeserializer<AbilityCategory> ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(AbilityCategory.class, buffer -> Registries.ABILITY_CATEGORIES.byId(buffer.readInt()));
    public static final FBBDeserializer<Skill> SKILL_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Skill.class, buffer -> Registries.SKILLS.byId(buffer.readInt()));
    public static final FBBDeserializer<BlockPos> BLOCK_POS_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(BlockPos.class, buffer -> buffer.readBlockPos());
    public static final FBBDeserializer<UUID> UUID_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(UUID.class, buffer -> buffer.readUUID());
    public static final FBBDeserializer<Vector3f> VECTOR_3_F_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Vector3f.class, buffer -> buffer.readVector3f());
    public static final FBBDeserializer<Tag> COMPOUND_TAG_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Tag.class, buffer -> buffer.readNbt());
    public static final FBBDeserializer<ArrayList> ARRAY_LIST_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(ArrayList.class, buffer -> {
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
    public static final FBBDeserializer<HashMap> MAP_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(HashMap.class, buffer -> {
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
    public static final FBBDeserializer<HashSet> HASH_SET_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(HashSet.class, buffer -> {
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
    public static final FBBDeserializer<Class> CLASS_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(Class.class, buffer -> {
        try {
            return Class.forName(buffer.readUtf());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    });
    public static final FBBDeserializer<ImmutablePair> PAIR_FRIENDLY_BYTE_BUF_DESERIALIZER = registerDeserializer(ImmutablePair.class, buffer -> {
        var leftTypeId = buffer.readVarInt();
        var rightTypeId = buffer.readVarInt();
        var leftDeserializer = getRequiredDeserializer(leftTypeId);
        var rightDeserializer = getRequiredDeserializer(rightTypeId);

        var left = leftDeserializer.deserialize(buffer);
        var right = rightDeserializer.deserialize(buffer);

        return new ImmutablePair<>(left, right);
    });

    public static <T, C extends Collection<T>> FBBDeserializer<C> getCollectionFriendlyByteBufDeserializer(Class<T> elementType, Supplier<C> collectionFactory) {
        return buffer -> {
            var collection = collectionFactory.get();
            var size = buffer.readVarInt();
            var elementDeserializer = (FBBDeserializer<T>) getRequiredDeserializer(elementType);
            for (var i = 0; i < size; i++) {
                collection.add(elementDeserializer.deserialize(buffer));
            }
            return collection;
        };
    }

    public static <T> FBBDeserializer<T> registerDeserializer(
            Class<T> clazz, FBBDeserializer<T> deserializer
    ) {
        DESERIALIZER_IDS.put(deserializer, DESERIALIZER_IDS.size());
        DESERIALIZER_MAP.put(clazz, deserializer);
        return deserializer;
    }

    public static int getDeserializerId(FBBDeserializer<?> serializer) {
        return DESERIALIZER_IDS.get(serializer);
    }

    public static int getDeserializerId(Class<?> clazz) {
        return DESERIALIZER_IDS.get(getRequiredDeserializer(clazz));
    }

    @Nullable
    public static <T> FBBDeserializer<T> getDeserializer(int id) {
        return (FBBDeserializer<T>) DESERIALIZER_IDS.inverse().get(id);
    }

    public static <T> FBBDeserializer<T> getRequiredDeserializer(int id) {
        var deserializer = getDeserializer(id);
        if (deserializer == null) {
            throw new RuntimeException("No deserializer found for id " + id);
        } else {
            return (FBBDeserializer<T>) deserializer;
        }
    }

    @Nullable
    public static <T> FBBDeserializer<T> getDeserializer(Class<?> clazz) {
        return (FBBDeserializer<T>) DESERIALIZER_MAP.get(clazz);
    }

    public static <T> FBBDeserializer<T> getRequiredDeserializer(Class<?> clazz) {
        var deserializer = getDeserializer(clazz);
        if (deserializer == null) {
            for (var entry : DESERIALIZER_MAP.entrySet()) {
                if (entry.getKey().isAssignableFrom(clazz)) {
                    return (FBBDeserializer<T>) entry.getValue();
                }
            }
            throw new NullPointerException("Deserializer for " + clazz.getCanonicalName() + " was null and no assignable deserializer found.");
        } else {
            return (FBBDeserializer<T>) deserializer;
        }
    }
}