package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.packet.FuturePacket;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.academy.api.common.vanilla.ThreadType;

@PacketTarget(ThreadType.SERVER)
public class C2SFuturePacket extends FuturePacket<ServerGamePacketListenerImpl> {
    @ReceiverConstructor
    public C2SFuturePacket() {
    }

    @SenderConstructor
    public C2SFuturePacket(int futureId, Class<? extends FutureRequestPayload> requestPayloadClass, FriendlyByteBuf payloadDataForRequest) {
        super(futureId, requestPayloadClass, payloadDataForRequest);
    }

    @SenderConstructor
    public C2SFuturePacket(int futureId, Object responseData) {
        super(futureId, responseData);
    }
}