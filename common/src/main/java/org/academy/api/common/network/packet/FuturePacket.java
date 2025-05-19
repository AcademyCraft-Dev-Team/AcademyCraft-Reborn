package org.academy.api.common.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.academy.api.common.network.*;
import org.jetbrains.annotations.NotNull;

public abstract class FuturePacket<T extends PacketListener> extends IPacket<T> {
    public int futureId;
    public boolean isResponse;
    public int payloadTypeId;
    public FriendlyByteBuf payloadData;

    @ReceiverConstructor
    public FuturePacket() {
    }

    @SenderConstructor
    public FuturePacket(int futureId, Class<? extends FutureRequestPayload> requestPayloadClass, FriendlyByteBuf payloadDataForRequest) {
        this.futureId = futureId;
        this.isResponse = false;
        this.payloadTypeId = requestPayloadClass.getName().hashCode();
        this.payloadData = payloadDataForRequest;
    }

    @SenderConstructor
    public FuturePacket(int futureId, Object responseData) {
        this.futureId = futureId;
        this.isResponse = true;
        Class<?> responseClass = responseData.getClass();
        this.payloadTypeId = FriendlyByteBufDeserializers.getDeserializerId(responseClass);
        this.payloadData = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBufSerializer<Object> serializer = FriendlyByteBufSerializers.getRequiredSerializer(responseClass);
        serializer.serialize(this.payloadData, responseData);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.futureId = buf.readVarInt();
        this.isResponse = buf.readBoolean();
        this.payloadTypeId = buf.readVarInt();
        this.payloadData = new FriendlyByteBuf(buf.readBytes(buf.readableBytes()));
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeVarInt(futureId);
        buf.writeBoolean(isResponse);
        buf.writeVarInt(payloadTypeId);
        buf.writeBytes(payloadData.copy());
    }
}