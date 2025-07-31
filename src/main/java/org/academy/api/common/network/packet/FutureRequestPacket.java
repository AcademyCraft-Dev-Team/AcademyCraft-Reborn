package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.academy.api.common.network.PacketType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

public final class FutureRequestPacket<T extends PacketListener> extends FuturePacket<T> {
    public FutureRequestPacket(T packetListener) {
        super(packetListener);
    }

    public FutureRequestPacket(int futureId, int requestPayloadTypeId, FriendlyByteBuf payloadData) {
        super(futureId, requestPayloadTypeId, payloadData);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull PacketType<T, ? extends Packet<T>> getPacketType() {
        return (PacketType<T, ? extends Packet<T>>) PacketTypes.FUTURE_REQUEST.get();
    }
}