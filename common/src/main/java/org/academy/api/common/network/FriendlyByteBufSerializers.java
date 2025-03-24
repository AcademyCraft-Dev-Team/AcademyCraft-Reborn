package org.academy.api.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 需要注意的是泛型的类型不能为 Object 或类似的
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class FriendlyByteBufSerializers {
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
    public static final FriendlyByteBufSerializer<Component> COMPONENT_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Component.class, FriendlyByteBuf::writeComponent);
    public static final FriendlyByteBufSerializer<Vector3f> VECTOR_3_F_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Vector3f.class, FriendlyByteBuf::writeVector3f);
    public static final FriendlyByteBufSerializer<Tag> COMPOUND_TAG_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Tag.class, (buffer, value) -> buffer.writeNbt((CompoundTag) value));
    public static final FriendlyByteBufSerializer<ArrayList> ARRAY_LIST_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(ArrayList.class, (buffer, value) -> {
        if (!value.isEmpty()) {
            buffer.writeBoolean(true);
            buffer.writeVarInt(value.size());
            Class<?> commonSuperclass = value.get(0).getClass();
            for (Object obj : value) {
                while (!commonSuperclass.isAssignableFrom(obj.getClass())) {
                    commonSuperclass = commonSuperclass.getSuperclass();
                }
            }
            AcademyCraft.LOGGER.info(commonSuperclass.getCanonicalName());
            buffer.writeUtf(commonSuperclass.getCanonicalName());
            for (Object o : value) {
                AcademyCraft.LOGGER.info(o.toString());
                FriendlyByteBufSerializer serializer = getRequiredSerializer(commonSuperclass);
                serializer.serialize(buffer, o);
            }
        } else {
            buffer.writeBoolean(false);
        }
    });
    public static final FriendlyByteBufSerializer<Map> MAP_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Map.class, (buffer, value) -> {
        buffer.writeVarInt(value.size());
        buffer.writeBoolean(value.isEmpty());
        if (!value.isEmpty()) {
            buffer.writeUtf(value.keySet().iterator().next().getClass().getCanonicalName());
            buffer.writeUtf(value.get(0).getClass().getCanonicalName());
            value.forEach((k, v) -> {
                FriendlyByteBufSerializer keySerializer = getRequiredSerializer(k.getClass());
                FriendlyByteBufSerializer valueSerializer = getRequiredSerializer(v.getClass());
                keySerializer.serialize(buffer, k);
                valueSerializer.serialize(buffer, v);
            });
        }
    });
    public static final FriendlyByteBufSerializer<FriendlyByteBufSerializers> FRIENDLY_BYTE_BUF_SERIALIZERS_FRIENDLY_BYTE_BUF_SERIALIZERS = registerSerializer(FriendlyByteBufSerializers.class, (buffer, value) -> {
        buffer.writeUtf(value.getClass().getCanonicalName());

    });
    public static final FriendlyByteBufSerializer<Class> CLASS_FRIENDLY_BYTE_BUF_SERIALIZER = registerSerializer(Class.class, (buffer, value) -> buffer.writeUtf(value.getCanonicalName()));

    public static <T> FriendlyByteBufSerializer<T> registerSerializer(Class<T> clazz, FriendlyByteBufSerializer<T> serializer) {
        FRIENDLY_BYTE_BUF_SERIALIZER_MAP.put(clazz, serializer);
        return serializer;
    }

    @Nullable
    public static <T> FriendlyByteBufSerializer<T> getSerializer(Class<T> clazz) {
        return (FriendlyByteBufSerializer<T>) FRIENDLY_BYTE_BUF_SERIALIZER_MAP.get(clazz);
    }

    /**
     * 所有单接口类，但是没有注册的，则会使用接口的，见 Collection
     */
    public static <T> FriendlyByteBufSerializer<T> getRequiredSerializer(Class<T> clazz) {
        AcademyCraft.LOGGER.info("Get required serializer for {}", clazz);
        FriendlyByteBufSerializer<T> serializer = getSerializer(clazz);
        if (serializer == null) {
            throw new NullPointerException("Serializer for " + clazz.getCanonicalName() + " was null");
        } else {
            return serializer;
        }
    }

    private FriendlyByteBufSerializers() {
    }
}