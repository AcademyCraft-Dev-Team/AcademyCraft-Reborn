package org.academy.api.common.util;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.FriendlyByteBufDeserializer;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.FriendlyByteBufSerializer;
import org.academy.api.common.network.FriendlyByteBufSerializers;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FriendlyByteBufUtil {
    public static FriendlyByteBuf autoSerializable(Object... values) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        for (Object value : values) {
            FriendlyByteBufSerializer friendlyByteBufSerializer = FriendlyByteBufSerializers.getRequiredSerializer(value.getClass());
            friendlyByteBufSerializer.serialize(friendlyByteBuf, value);
        }
        return friendlyByteBuf;
    }

    public static Object[] autoDeserializable(FriendlyByteBuf buf, Class<?>... types) {
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            FriendlyByteBufDeserializer deserializer = FriendlyByteBufDeserializers.getRequiredDeserializer(types[i]);
            values[i] = deserializer.deserialize(buf);
        }
        return values;
    }
}