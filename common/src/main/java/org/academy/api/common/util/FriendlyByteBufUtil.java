package org.academy.api.common.util;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.FriendlyByteBufSerializers;

public class FriendlyByteBufUtil {
    public static FriendlyByteBuf autoSerializable(Object... values) {
        var friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        for (var value : values) {
            var friendlyByteBufSerializer = FriendlyByteBufSerializers.getRequiredSerializer(value.getClass());
            friendlyByteBufSerializer.serialize(friendlyByteBuf, value);
        }
        return friendlyByteBuf;
    }

    public static Object[] autoDeserializable(FriendlyByteBuf buf, Class<?>... types) {
        var values = new Object[types.length];
        for (var i = 0; i < types.length; i++) {
            var deserializer = FriendlyByteBufDeserializers.getRequiredDeserializer(types[i]);
            values[i] = deserializer.deserialize(buf);
        }
        return values;
    }
}