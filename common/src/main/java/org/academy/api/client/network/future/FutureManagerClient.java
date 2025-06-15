package org.academy.api.client.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.future.AbstractFutureManager;
import org.academy.api.common.network.future.FutureManager;
import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.FutureRequestPacket;
import org.academy.api.common.network.packet.FutureResponsePacket;

import java.util.function.Consumer;

public class FutureManagerClient extends AbstractFutureManager {
    public FutureManagerClient(FutureManager futureManager) {
        super(futureManager);
    }

    public <T_RESP extends IResponsePayload, T_REQ_LISTENER extends PacketListener, REQUEST extends IRequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToServer(
            REQUEST requestPayload, Consumer<T_RESP> callback, long timeoutMillis) {
        int futureId = createPendingFuture(requestPayload.getExpectedResponseType(), callback, timeoutMillis);
        if (futureId == -1) return;

        int requestTypeId = futureManager.getPayloadId(requestPayload.getClass());
        FriendlyByteBuf payloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        requestPayload.write(payloadBuffer);

        FutureRequestPacket<ServerGamePacketListenerImpl> packet = new FutureRequestPacket<>(futureId, requestTypeId, payloadBuffer);
        NetworkManagerClient.sendPacket(new C2SPacket(packet));
    }

    public <T_RESP extends IResponsePayload, T_REQ_LISTENER extends PacketListener, REQUEST extends IRequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToServer(
            REQUEST requestPayload, Consumer<T_RESP> callback) {
        sendRequestToServer(requestPayload, callback, DEFAULT_TIMEOUT_MS);
    }

    @SubscribePacket
    public void handleFutureRequestFromServer(FutureRequestPacket<ClientPacketListener> requestPacket) {
        handleRequest(requestPacket, requestPacket.packetListenerSupplier, response -> {
            int responseTypeId = futureManager.getPayloadId(response.getClass());
            FriendlyByteBuf responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
            response.write(responseBuffer);
            FutureResponsePacket<ServerGamePacketListenerImpl> responsePkt = new FutureResponsePacket<>(requestPacket.futureId, responseTypeId, responseBuffer);
            NetworkManagerClient.sendPacket(new C2SPacket(responsePkt));
        });
    }

    @SubscribePacket
    public void handleFutureResponseFromServer(FutureResponsePacket<ClientPacketListener> responsePacket) {
        handleResponse(responsePacket, payload -> Minecraft.getInstance().execute(() -> executeCallback(responsePacket.futureId, payload)));
    }
}