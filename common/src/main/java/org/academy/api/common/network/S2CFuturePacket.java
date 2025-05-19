package org.academy.api.common.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.packet.FuturePacket;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.academy.api.common.vanilla.ThreadType;

@PacketTarget(ThreadType.CLIENT)
public class S2CFuturePacket extends FuturePacket<ClientPacketListener> {
    @ReceiverConstructor
    public S2CFuturePacket() {
    }

    @SenderConstructor
    public S2CFuturePacket(int futureId, Class<? extends FutureRequestPayload> requestPayloadClass, FriendlyByteBuf payloadDataForRequest) {
        super(futureId, requestPayloadClass, payloadDataForRequest);
    }

    @SenderConstructor
    public S2CFuturePacket(int futureId, Object responseData) {
        super(futureId, responseData);
    }
}