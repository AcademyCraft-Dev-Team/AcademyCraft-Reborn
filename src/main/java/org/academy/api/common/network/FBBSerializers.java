package org.academy.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.common.extensions.IFriendlyByteBufExtension;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.registries.Registries;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public class FBBSerializers {
    public static final BiMap<FBBSerializer, Integer> SERIALIZER_IDS = HashBiMap.create();
    private static final Map<Class<?>, FBBSerializer<?>> SERIALIZER_MAP = new ConcurrentHashMap<>();
    public static final FBBSerializer<String> STRING = registerSerializer(String.class, FriendlyByteBuf::writeUtf);
    public static final FBBSerializer<Integer> INTEGER = registerSerializer(Integer.class, FriendlyByteBuf::writeVarInt);
    public static final FBBSerializer<Long> LONG = registerSerializer(Long.class, FriendlyByteBuf::writeVarLong);
    public static final FBBSerializer<Float> FLOAT = registerSerializer(Float.class, FriendlyByteBuf::writeFloat);
    public static final FBBSerializer<Double> DOUBLE = registerSerializer(Double.class, FriendlyByteBuf::writeDouble);
    public static final FBBSerializer<Boolean> BOOLEAN = registerSerializer(Boolean.class, FriendlyByteBuf::writeBoolean);
    public static final FBBSerializer<Byte> BYTE = registerSerializer(Byte.class, IFriendlyByteBufExtension::writeByte);
    public static final FBBSerializer<Short> SHORT = registerSerializer(Short.class, (buffer, value) -> buffer.writeShort(value));
    public static final FBBSerializer<Character> CHAR = registerSerializer(Character.class, (buffer, value) -> buffer.writeChar(value));
    public static final FBBSerializer<AbilityCategory> ABILITY_CATEGORY = registerSerializer(AbilityCategory.class, (buffer, value) -> buffer.writeInt(Registries.ABILITY_CATEGORIES.getId(value)));
    public static final FBBSerializer<Skill> SKILL = registerSerializer(Skill.class, (buffer, value) -> buffer.writeInt(Registries.SKILLS.getId(value)));
    public static final FBBSerializer<BlockPos> BLOCK_POS = registerSerializer(BlockPos.class, (buffer, value) -> buffer.writeBlockPos(value));
    public static final FBBSerializer<UUID> UUID = registerSerializer(UUID.class, (buffer, value) -> buffer.writeUUID(value));
    public static final FBBSerializer<Vector3f> VECTOR_3F = registerSerializer(Vector3f.class, (buffer, value) -> buffer.writeVector3f(value));
    public static final FBBSerializer<Tag> COMPOUND_TAG = registerSerializer(Tag.class, (buffer, value) -> buffer.writeNbt(value));
    public static final FBBSerializer<ArrayList> ARRAY_LIST = registerSerializer(ArrayList.class, (buffer, value) -> {
        if (!value.isEmpty()) {
            buffer.writeBoolean(true);
            var firstElement = value.getFirst();
            var elementTypeId = getSerializerId(firstElement.getClass());
            buffer.writeVarInt(elementTypeId);

            buffer.writeVarInt(value.size());
            var elementSerializer = getRequiredSerializer(firstElement.getClass());
            for (var element : value) {
                elementSerializer.serialize(buffer, element);
            }
        } else {
            buffer.writeBoolean(false);
        }
    });
    public static final FBBSerializer<HashMap> HASH_MAP = registerSerializer(HashMap.class, (buffer, value) -> {
        buffer.writeVarInt(value.size());
        buffer.writeBoolean(value.isEmpty());
        if (!value.isEmpty()) {
            var firstEntry = (Map.Entry) value.entrySet().iterator().next();
            var keyTypeId = getSerializerId(firstEntry.getKey().getClass());
            var valueTypeId = getSerializerId(firstEntry.getValue().getClass());
            buffer.writeVarInt(keyTypeId);
            buffer.writeVarInt(valueTypeId);

            var keySerializer = getRequiredSerializer(firstEntry.getKey().getClass());
            var valueSerializer = getRequiredSerializer(firstEntry.getValue().getClass());

            value.forEach((k, v) -> {
                keySerializer.serialize(buffer, k);
                valueSerializer.serialize(buffer, v);
            });
        }
    });
    public static final FBBSerializer<HashSet> HASH_SET = registerSerializer(HashSet.class, (buffer, value) -> {
        if (!value.isEmpty()) {
            buffer.writeBoolean(true);
            var firstElement = value.iterator().next();
            var elementTypeId = getSerializerId(firstElement.getClass());
            buffer.writeVarInt(elementTypeId);

            buffer.writeVarInt(value.size());
            var elementSerializer = getRequiredSerializer(firstElement.getClass());
            for (var element : value) {
                elementSerializer.serialize(buffer, element);
            }
        } else {
            buffer.writeBoolean(false);
        }
    });
    public static final FBBSerializer<FBBSerializers> FBB_SERIALIZERS = registerSerializer(FBBSerializers.class, (buffer, value) -> buffer.writeUtf(value.getClass().getCanonicalName()));
    public static final FBBSerializer<Class> CLASS = registerSerializer(Class.class, (buffer, value) -> buffer.writeUtf(value.getCanonicalName()));
    public static final FBBSerializer<ArrayList<Skill>> SKILL_ARRAY_LIST = getCollectionFriendlyByteBufSerializer(Skill.class);
    public static final FBBSerializer<ImmutablePair> PAIR_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(ImmutablePair.class, (buffer, pair) -> {
        var left = pair.getLeft();
        var right = pair.getRight();

        var leftTypeId = getSerializerId(left.getClass());
        var rightTypeId = getSerializerId(right.getClass());
        buffer.writeVarInt(leftTypeId);
        buffer.writeVarInt(rightTypeId);

        var leftSerializer = getRequiredSerializer(left.getClass());
        var rightSerializer = getRequiredSerializer(right.getClass());

        leftSerializer.serialize(buffer, left);
        rightSerializer.serialize(buffer, right);
    });
    public static final FBBSerializer<BlockPos.MutableBlockPos> MUTABLE_BLOCK_POS_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(BlockPos.MutableBlockPos.class, (buffer, value) -> buffer.writeBlockPos(value));

    private FBBSerializers() {
    }

    public static <T, C extends Collection<T>> FBBSerializer<C> getCollectionFriendlyByteBufSerializer(Class<T> elementType) {
        return (buffer, collection) -> {
            buffer.writeVarInt(collection.size());
            var elementSerializer = getRequiredSerializer(elementType);
            for (var element : collection) {
                elementSerializer.serialize(buffer, element);
            }
        };
    }

    public static <T> FBBSerializer<T> registerSerializer(
            Class<T> clazz, FBBSerializer<T> serializer
    ) {
        SERIALIZER_IDS.put(serializer, SERIALIZER_IDS.size());
        SERIALIZER_MAP.put(clazz, serializer);
        return serializer;
    }

    public static int getSerializerId(Class<?> clazz) {
        return SERIALIZER_IDS.get(getRequiredSerializer(clazz));
    }

    public static int getSerializerId(FBBSerializer<?> serializer) {
        return SERIALIZER_IDS.get(serializer);
    }

    @Nullable
    public static <T> FBBSerializer<T> getSerializer(int id) {
        return (FBBSerializer<T>) SERIALIZER_IDS.inverse().get(id);
    }

    @Nullable
    public static <T> FBBSerializer<T> getSerializer(Class<?> clazz) {
        return (FBBSerializer<T>) SERIALIZER_MAP.get(clazz);
    }

    public static <T> FBBSerializer<T> getRequiredSerializer(Class<?> clazz) {
        var serializer = getSerializer(clazz);
        if (serializer == null) {
            for (var entry : SERIALIZER_MAP.entrySet()) {
                if (entry.getKey().isAssignableFrom(clazz)) {
                    return (FBBSerializer<T>) entry.getValue();
                }
            }
            throw new NullPointerException("Serializer for " + clazz.getCanonicalName() + " was null and no assignable serializer found.");
        } else {
            return (FBBSerializer<T>) serializer;
        }
    }
}