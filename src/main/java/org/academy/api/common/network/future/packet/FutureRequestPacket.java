package org.academy.api.common.network.future.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.network.PacketType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class FutureRequestPacket<T extends PacketListener> extends FuturePacket<T, FutureRequestPacket<T>> {
    public static final StreamCodec<ByteBuf, FutureRequestPacket<?>> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            FuturePacket::getFutureId,
            ByteBufCodecs.INT,
            FuturePacket::getTargetPacketTypeId,
            ByteBufCodecs.BYTE_ARRAY,
            FuturePacket::getBytes,
            FutureRequestPacket::new
    );

    public FutureRequestPacket(int futureId, int responsePayloadTypeId, byte[] bytes) {
        super(futureId, responsePayloadTypeId, bytes);
    }

    @SuppressWarnings("unchecked")
    @Override
    public PacketType<T, FutureRequestPacket<T>> getPacketType() {
        return (PacketType<T, FutureRequestPacket<T>>) PacketTypes.FUTURE_REQUEST.get();
    }
}