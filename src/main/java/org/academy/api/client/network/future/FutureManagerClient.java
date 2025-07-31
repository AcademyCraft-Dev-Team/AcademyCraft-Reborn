package org.academy.api.client.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.future.AbstractFutureManager;
import org.academy.api.common.network.future.RequestPayload;
import org.academy.api.common.network.future.ResponsePayload;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.FutureRequestPacket;
import org.academy.api.common.network.packet.FutureResponsePacket;

import java.util.function.Consumer;

public class FutureManagerClient extends AbstractFutureManager {
    public FutureManagerClient() {
    }

    public <T_RESP extends ResponsePayload<?>, T_REQ_LISTENER extends PacketListener, REQUEST extends RequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToServer(
            REQUEST requestPayload, Consumer<T_RESP> callback, long timeoutMillis) {
        var futureId = createPendingFuture(requestPayload.getExpectedResponsePayloadType(), callback, timeoutMillis);
        if (futureId == -1) return;

        var requestTypeId = requestPayload.getPayloadType().getPayloadId();
        var payloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        requestPayload.write(payloadBuffer);

        var packet = new FutureRequestPacket<ServerGamePacketListenerImpl>(futureId, requestTypeId, payloadBuffer);
        AcademyCraftClient.sendPacket(new C2SPacket(packet));
    }

    public <T_RESP extends ResponsePayload<?>, T_REQ_LISTENER extends PacketListener, REQUEST extends RequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToServer(
            REQUEST requestPayload, Consumer<T_RESP> callback) {
        sendRequestToServer(requestPayload, callback, DEFAULT_TIMEOUT_MS);
    }

    @SubscribePacket
    public void handleFutureRequestFromServer(FutureRequestPacket<ClientPacketListener> requestPacket) {
        handleRequest(requestPacket, requestPacket.getPacketListener(), response -> {
            var responseTypeId = response.getPayloadType().getPayloadId();
            var responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
            response.write(responseBuffer);
            var responsePkt = new FutureResponsePacket<ServerGamePacketListenerImpl>(requestPacket.futureId, responseTypeId, responseBuffer);
            AcademyCraftClient.sendPacket(new C2SPacket(responsePkt));
        });
    }

    @SubscribePacket
    public void handleFutureResponseFromServer(FutureResponsePacket<ClientPacketListener> responsePacket) {
        handleResponse(responsePacket, payload -> Minecraft.getInstance().execute(() -> executeCallback(responsePacket.futureId, payload)));
    }
}