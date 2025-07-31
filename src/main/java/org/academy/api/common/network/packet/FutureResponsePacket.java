package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.academy.api.common.network.PacketType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

public final class FutureResponsePacket<T extends PacketListener> extends FuturePacket<T> {
    public FutureResponsePacket(T packetListener) {
        super(packetListener);
    }

    public FutureResponsePacket(int futureId, int responsePayloadTypeId, FriendlyByteBuf payloadData) {
        super(futureId, responsePayloadTypeId, payloadData);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull PacketType<T, ? extends IPacket<T>> getPacketType() {
        return (PacketType<T, ? extends IPacket<T>>) PacketTypes.FUTURE_RESPONSE.get();
    }
}