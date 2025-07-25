package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;

public final class FutureRequestPacket<T extends PacketListener> extends FuturePacket<T> {
    public FutureRequestPacket(T packetListener) {
        super(packetListener);
    }

    public FutureRequestPacket(int futureId, int requestPayloadTypeId, FriendlyByteBuf payloadData) {
        super(futureId, requestPayloadTypeId, payloadData);
    }
}