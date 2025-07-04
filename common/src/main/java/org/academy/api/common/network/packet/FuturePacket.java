package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.jetbrains.annotations.NotNull;

public abstract class FuturePacket<T extends PacketListener> extends IPacket<T> {
    public int futureId;
    public int payloadTypeId;
    public FriendlyByteBuf payloadData;

    public FuturePacket() {
    }

    protected FuturePacket(int newFutureId, int newPayloadTypeId, FriendlyByteBuf newPayloadData) {
        futureId = newFutureId;
        payloadTypeId = newPayloadTypeId;
        payloadData = newPayloadData;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        futureId = buf.readVarInt();
        payloadTypeId = buf.readVarInt();
        var readableBytes = buf.readableBytes();
        if (readableBytes > 0) {
            payloadData = new FriendlyByteBuf(buf.readBytes(readableBytes));
        } else {
            payloadData = new FriendlyByteBuf(Unpooled.buffer(0));
        }
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeVarInt(futureId);
        buf.writeVarInt(payloadTypeId);
        if (payloadData != null && payloadData.readableBytes() > 0) {
            buf.writeBytes(payloadData.copy());
        }
    }
}