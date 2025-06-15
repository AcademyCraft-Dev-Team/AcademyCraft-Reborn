package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public class FriendlyByteBufSerializers {
    public static final BiMap<FriendlyByteBufSerializer, Integer> SERIALIZER_IDS = HashBiMap.create();
    private static final Map<Class<?>, FriendlyByteBufSerializer<?>> FRIENDLY_BYTE_BUF_SERIALIZER_MAP = new ConcurrentHashMap<>();
    public static final FriendlyByteBufSerializer<String> STRING_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(String.class, FriendlyByteBuf::writeUtf);
    public static final FriendlyByteBufSerializer<Integer> INTEGER_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Integer.class, FriendlyByteBuf::writeVarInt);
    public static final FriendlyByteBufSerializer<Long> LONG_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Long.class, FriendlyByteBuf::writeVarLong);
    public static final FriendlyByteBufSerializer<Float> FLOAT_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Float.class, FriendlyByteBuf::writeFloat);
    public static final FriendlyByteBufSerializer<Double> DOUBLE_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Double.class, FriendlyByteBuf::writeDouble);
    public static final FriendlyByteBufSerializer<Boolean> BOOLEAN_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Boolean.class, FriendlyByteBuf::writeBoolean);
    public static final FriendlyByteBufSerializer<Byte> BYTE_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Byte.class, (buffer, value) -> buffer.writeByte(value));
    public static final FriendlyByteBufSerializer<Short> SHORT_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Short.class, (buffer, value) -> buffer.writeShort(value));
    public static final FriendlyByteBufSerializer<Character> CHAR_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Character.class, (buffer, value) -> buffer.writeChar(value));
    public static final FriendlyByteBufSerializer<AbilityCategory> ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(AbilityCategory.class, (buffer, value) -> buffer.writeUtf(value.name));
    public static final FriendlyByteBufSerializer<Skill> SKILL_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Skill.class, (buffer, value) -> buffer.writeUtf(value.name));
    public static final FriendlyByteBufSerializer<BlockPos> BLOCK_POS_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(BlockPos.class, FriendlyByteBuf::writeBlockPos);
    public static final FriendlyByteBufSerializer<UUID> UUID_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(UUID.class, FriendlyByteBuf::writeUUID);
    public static final FriendlyByteBufSerializer<MutableComponent> COMPONENT_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(MutableComponent.class, FriendlyByteBuf::writeComponent);
    public static final FriendlyByteBufSerializer<Vector3f> VECTOR_3_F_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Vector3f.class, FriendlyByteBuf::writeVector3f);
    public static final FriendlyByteBufSerializer<Tag> COMPOUND_TAG_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Tag.class, (buffer, value) -> buffer.writeNbt((CompoundTag) value));
    public static final FriendlyByteBufSerializer<ArrayList> ARRAY_LIST_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(ArrayList.class, (buffer, value) -> {
        if (!value.isEmpty()) {
            buffer.writeBoolean(true);
            Object firstElement = value.get(0);
            int elementTypeId = getSerializerId(firstElement.getClass());
            buffer.writeVarInt(elementTypeId);

            buffer.writeVarInt(value.size());
            FriendlyByteBufSerializer elementSerializer = getRequiredSerializer(firstElement.getClass());
            for (Object element : value) {
                elementSerializer.serialize(buffer, element);
            }
        } else {
            buffer.writeBoolean(false);
        }
    });
    public static final FriendlyByteBufSerializer<HashMap> MAP_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(HashMap.class, (buffer, value) -> {
        buffer.writeVarInt(value.size());
        buffer.writeBoolean(value.isEmpty());
        if (!value.isEmpty()) {
            Map.Entry firstEntry = (Map.Entry) value.entrySet().iterator().next();
            int keyTypeId = getSerializerId(firstEntry.getKey().getClass());
            int valueTypeId = getSerializerId(firstEntry.getValue().getClass());
            buffer.writeVarInt(keyTypeId);
            buffer.writeVarInt(valueTypeId);

            FriendlyByteBufSerializer keySerializer = getRequiredSerializer(firstEntry.getKey().getClass());
            FriendlyByteBufSerializer valueSerializer = getRequiredSerializer(firstEntry.getValue().getClass());

            value.forEach((k, v) -> {
                keySerializer.serialize(buffer, k);
                valueSerializer.serialize(buffer, v);
            });
        }
    });
    public static final FriendlyByteBufSerializer<HashSet> HASH_SET_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(HashSet.class, (buffer, value) -> {
        if (!value.isEmpty()) {
            buffer.writeBoolean(true);
            Object firstElement = value.iterator().next();
            int elementTypeId = getSerializerId(firstElement.getClass());
            buffer.writeVarInt(elementTypeId);

            buffer.writeVarInt(value.size());
            FriendlyByteBufSerializer elementSerializer = getRequiredSerializer(firstElement.getClass());
            for (Object element : value) {
                elementSerializer.serialize(buffer, element);
            }
        } else {
            buffer.writeBoolean(false);
        }
    });
    public static final FriendlyByteBufSerializer<FriendlyByteBufSerializers> FRIENDLY_BYTE_BUF_SERIALIZERS_FRIENDLY_BYTE_BUF_SERIALIZERS = registerSerializer(FriendlyByteBufSerializers.class, (buffer, value) -> buffer.writeUtf(value.getClass().getCanonicalName()));
    public static final FriendlyByteBufSerializer<Class> CLASS_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Class.class, (buffer, value) -> buffer.writeUtf(value.getCanonicalName()));
    public static final FriendlyByteBufSerializer<ArrayList<Skill>> SKILL_ARRAY_LIST_FRIENDLY_BYTE_BUF_SERIALIZER = getCollectionFriendlyByteBufSerializer(Skill.class);
    public static final FriendlyByteBufSerializer<ImmutablePair> PAIR_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(ImmutablePair.class, (buffer, pair) -> {
        Object left = pair.getLeft();
        Object right = pair.getRight();

        int leftTypeId = getSerializerId(left.getClass());
        int rightTypeId = getSerializerId(right.getClass());
        buffer.writeVarInt(leftTypeId);
        buffer.writeVarInt(rightTypeId);

        FriendlyByteBufSerializer leftSerializer = getRequiredSerializer(left.getClass());
        FriendlyByteBufSerializer rightSerializer = getRequiredSerializer(right.getClass());

        leftSerializer.serialize(buffer, left);
        rightSerializer.serialize(buffer, right);
    });
    public static final FriendlyByteBufSerializer<BlockPos.MutableBlockPos> MUTABLE_BLOCK_POS_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(BlockPos.MutableBlockPos.class, FriendlyByteBuf::writeBlockPos);

    private FriendlyByteBufSerializers() {
    }

    public static <T, C extends Collection<T>> FriendlyByteBufSerializer<C> getCollectionFriendlyByteBufSerializer(Class<T> elementType) {
        return (buffer, collection) -> {
            buffer.writeVarInt(collection.size());
            FriendlyByteBufSerializer<T> elementSerializer = getRequiredSerializer(elementType);
            for (T element : collection) {
                elementSerializer.serialize(buffer, element);
            }
        };
    }

    public static <T> FriendlyByteBufSerializer<T> registerSerializer(
            Class<T> clazz, FriendlyByteBufSerializer<T> serializer
    ) {
        SERIALIZER_IDS.put(serializer, SERIALIZER_IDS.size());
        FRIENDLY_BYTE_BUF_SERIALIZER_MAP.put(clazz, serializer);
        return serializer;
    }

    public static int getSerializerId(Class<?> clazz) {
        return SERIALIZER_IDS.get(getRequiredSerializer(clazz));
    }

    public static int getSerializerId(FriendlyByteBufSerializer<?> serializer) {
        return SERIALIZER_IDS.get(serializer);
    }

    @Nullable
    public static <T> FriendlyByteBufSerializer<T> getSerializer(int id) {
        return (FriendlyByteBufSerializer<T>) SERIALIZER_IDS.inverse().get(id);
    }

    @Nullable
    public static <T> FriendlyByteBufSerializer<T> getSerializer(Class<?> clazz) {
        return (FriendlyByteBufSerializer<T>) FRIENDLY_BYTE_BUF_SERIALIZER_MAP.get(clazz);
    }

    public static <T> FriendlyByteBufSerializer<T> getRequiredSerializer(Class<?> clazz) {
        FriendlyByteBufSerializer<T> serializer = getSerializer(clazz);
        if (serializer == null) {
            for (Map.Entry<Class<?>, FriendlyByteBufSerializer<?>> entry : FRIENDLY_BYTE_BUF_SERIALIZER_MAP.entrySet()) {
                if (entry.getKey().isAssignableFrom(clazz)) {
                    return (FriendlyByteBufSerializer<T>) entry.getValue();
                }
            }
            throw new NullPointerException("Serializer for " + clazz.getCanonicalName() + " was null and no assignable serializer found.");
        } else {
            return serializer;
        }
    }
}