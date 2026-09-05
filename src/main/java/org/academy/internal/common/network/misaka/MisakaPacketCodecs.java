package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public final class MisakaPacketCodecs {
    public static final StreamCodec<ByteBuf, UUID> UUID_STREAM_CODEC = StreamCodec.of(
            MisakaPacketCodecs::encodeUuid,
            MisakaPacketCodecs::decodeUuid
    );

    private MisakaPacketCodecs() {
    }

    public static void encodeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID decodeUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }
}
