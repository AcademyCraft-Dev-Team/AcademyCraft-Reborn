package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.academy.api.common.network.ReceiverConstructor;
import org.academy.api.common.network.SenderConstructor;

public class FutureResponsePacket<T extends PacketListener> extends FuturePacket<T> {
    @ReceiverConstructor
    public FutureResponsePacket() {
        super();
    }

    @SenderConstructor
    public FutureResponsePacket(int futureId, int responsePayloadTypeId, FriendlyByteBuf payloadData) {
        super(futureId, responsePayloadTypeId, payloadData);
    }
}