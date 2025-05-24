package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.academy.api.common.network.ReceiverConstructor;
import org.academy.api.common.network.SenderConstructor;

public class FutureRequestPacket<T extends PacketListener> extends FuturePacket<T> {
    @ReceiverConstructor
    public FutureRequestPacket() {
        super();
    }

    @SenderConstructor
    public FutureRequestPacket(int futureId, int requestPayloadTypeId, FriendlyByteBuf payloadData) {
        super(futureId, requestPayloadTypeId, payloadData);
    }
}