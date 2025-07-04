package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;

public class FutureResponsePacket<T extends PacketListener> extends FuturePacket<T> {
    public FutureResponsePacket() {
    }

    public FutureResponsePacket(int futureId, int responsePayloadTypeId, FriendlyByteBuf payloadData) {
        super(futureId, responsePayloadTypeId, payloadData);
    }
}