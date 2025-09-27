package org.academy.api.common.network.future.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.network.packet.PacketType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class FutureResponsePacket<T extends PacketListener> extends FuturePacket<T, FutureResponsePacket<T>> {
    public static final StreamCodec<ByteBuf, FuturePacket<?, ?>> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            FuturePacket::getFutureId,
            ByteBufCodecs.INT,
            FuturePacket::getTargetPacketTypeId,
            ByteBufCodecs.BYTE_ARRAY,
            FuturePacket::getBytes,
            FutureResponsePacket::new
    );

    public FutureResponsePacket(int futureId, int responsePayloadTypeId, byte[] bytes) {
        super(futureId, responsePayloadTypeId, bytes);
    }

    @SuppressWarnings("unchecked")
    @Override
    public PacketType<T, FutureResponsePacket<T>> getPacketType() {
        return (PacketType<T, FutureResponsePacket<T>>) PacketTypes.FUTURE_RESPONSE.get();
    }
}