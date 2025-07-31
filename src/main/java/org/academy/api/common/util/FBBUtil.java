package org.academy.api.common.util;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.FBBDeserializers;
import org.academy.api.common.network.FBBSerializers;

public class FBBUtil {
    public static FriendlyByteBuf autoSerializable(Object... values) {
        var friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        for (var value : values) {
            var friendlyByteBufSerializer = FBBSerializers.getRequiredSerializer(value.getClass());
            friendlyByteBufSerializer.serialize(friendlyByteBuf, value);
        }
        return friendlyByteBuf;
    }

    public static Object[] autoDeserializable(FriendlyByteBuf buf, Class<?>... types) {
        var values = new Object[types.length];
        for (var i = 0; i < types.length; i++) {
            var deserializer = FBBDeserializers.getRequiredDeserializer(types[i]);
            values[i] = deserializer.deserialize(buf);
        }
        return values;
    }
}