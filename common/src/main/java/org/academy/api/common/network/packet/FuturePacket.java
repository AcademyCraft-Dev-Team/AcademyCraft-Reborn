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

    protected FuturePacket(int futureId, int payloadTypeId, FriendlyByteBuf payloadData) {
        this.futureId = futureId;
        this.payloadTypeId = payloadTypeId;
        this.payloadData = payloadData;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.futureId = buf.readVarInt();
        this.payloadTypeId = buf.readVarInt();
        int readableBytes = buf.readableBytes();
        if (readableBytes > 0) {
            this.payloadData = new FriendlyByteBuf(buf.readBytes(readableBytes));
        } else {
            this.payloadData = new FriendlyByteBuf(Unpooled.buffer(0));
        }
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeVarInt(futureId);
        buf.writeVarInt(payloadTypeId);
        if (this.payloadData != null && this.payloadData.readableBytes() > 0) {
            buf.writeBytes(payloadData.copy());
        }
    }
}