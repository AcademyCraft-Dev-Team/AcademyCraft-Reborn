package org.academy.api.server.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.future.AbstractFutureManager;
import org.academy.api.common.network.future.FutureManager;
import org.academy.api.common.network.future.IRequestPayload;
import org.academy.api.common.network.future.IResponsePayload;
import org.academy.api.common.network.packet.FutureRequestPacket;
import org.academy.api.common.network.packet.FutureResponsePacket;
import org.academy.api.common.network.packet.S2CPacket;

import java.util.function.Consumer;

public class FutureManagerServer extends AbstractFutureManager {
    public FutureManagerServer(FutureManager futureManager) {
        super(futureManager);
    }

    public <T_RESP extends IResponsePayload, T_REQ_LISTENER extends PacketListener, REQUEST extends IRequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToClient(
            ServerPlayer player, REQUEST requestPayload, Consumer<T_RESP> callback, long timeoutMillis) {
        var futureId = createPendingFuture(requestPayload.getExpectedResponseType(), callback, timeoutMillis);
        if (futureId == -1) return;

        var requestTypeId = futureManager.getPayloadId(requestPayload.getClass());
        var payloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        requestPayload.write(payloadBuffer);

        var packet = new FutureRequestPacket<ClientPacketListener>(futureId, requestTypeId, payloadBuffer);
        player.connection.send(new S2CPacket(packet));
    }

    public <T_RESP extends IResponsePayload, T_REQ_LISTENER extends PacketListener, REQUEST extends IRequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToClient(
            ServerPlayer player, REQUEST requestPayload, Consumer<T_RESP> callback) {
        sendRequestToClient(player, requestPayload, callback, DEFAULT_TIMEOUT_MS);
    }

    @SubscribePacket
    public void handleFutureRequestFromClient(FutureRequestPacket<ServerGamePacketListenerImpl> requestPacket) {
        var packetListener = requestPacket.getPacketListener();
        var player = packetListener.getPlayer();

        handleRequest(requestPacket, packetListener, response -> {
            var responseTypeId = futureManager.getPayloadId(response.getClass());
            var responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
            response.write(responseBuffer);
            var responsePkt = new FutureResponsePacket<ClientPacketListener>(requestPacket.futureId, responseTypeId, responseBuffer);
            player.connection.send(new S2CPacket(responsePkt));
        });
    }

    @SubscribePacket
    public void handleFutureResponseFromClient(FutureResponsePacket<ServerGamePacketListenerImpl> responsePacket) {
        var player = responsePacket.getPacketListener().getPlayer();

        handleResponse(responsePacket, payload -> player.server.execute(() -> executeCallback(responsePacket.futureId, payload)));
    }
}