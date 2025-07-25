package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;

public final class FutureResponsePacket<T extends PacketListener> extends FuturePacket<T> {
    public FutureResponsePacket(T packetListener) {
        super(packetListener);
    }

    public FutureResponsePacket(int futureId, int responsePayloadTypeId, FriendlyByteBuf payloadData) {
        super(futureId, responsePayloadTypeId, payloadData);
    }
}