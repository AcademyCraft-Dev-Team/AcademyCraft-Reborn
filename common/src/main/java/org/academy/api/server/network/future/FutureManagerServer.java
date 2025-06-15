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
import java.util.function.Supplier;

public class FutureManagerServer extends AbstractFutureManager {
    public FutureManagerServer(FutureManager futureManager) {
        super(futureManager);
    }

    public <T_RESP extends IResponsePayload, T_REQ_LISTENER extends PacketListener, REQUEST extends IRequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToClient(
            ServerPlayer player, REQUEST requestPayload, Consumer<T_RESP> callback, long timeoutMillis) {
        int futureId = createPendingFuture(requestPayload.getExpectedResponseType(), callback, timeoutMillis);
        if (futureId == -1) return;

        int requestTypeId = futureManager.getPayloadId(requestPayload.getClass());
        FriendlyByteBuf payloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        requestPayload.write(payloadBuffer);

        FutureRequestPacket<ClientPacketListener> packet = new FutureRequestPacket<>(futureId, requestTypeId, payloadBuffer);
        player.connection.send(new S2CPacket(packet));
    }

    public <T_RESP extends IResponsePayload, T_REQ_LISTENER extends PacketListener, REQUEST extends IRequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToClient(
            ServerPlayer player, REQUEST requestPayload, Consumer<T_RESP> callback) {
        sendRequestToClient(player, requestPayload, callback, DEFAULT_TIMEOUT_MS);
    }

    @SubscribePacket
    public void handleFutureRequestFromClient(FutureRequestPacket<ServerGamePacketListenerImpl> requestPacket) {
        Supplier<ServerGamePacketListenerImpl> supplier = requestPacket.packetListenerSupplier;
        if (supplier == null || supplier.get() == null) return;
        ServerPlayer player = supplier.get().player;

        handleRequest(requestPacket, supplier, response -> {
            int responseTypeId = futureManager.getPayloadId(response.getClass());
            FriendlyByteBuf responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
            response.write(responseBuffer);
            FutureResponsePacket<ClientPacketListener> responsePkt = new FutureResponsePacket<>(requestPacket.futureId, responseTypeId, responseBuffer);
            player.connection.send(new S2CPacket(responsePkt));
        });
    }

    @SubscribePacket
    public void handleFutureResponseFromClient(FutureResponsePacket<ServerGamePacketListenerImpl> responsePacket) {
        Supplier<ServerGamePacketListenerImpl> supplier = responsePacket.packetListenerSupplier;
        if (supplier == null || supplier.get() == null) return;
        ServerPlayer player = supplier.get().player;

        handleResponse(responsePacket, payload -> player.server.execute(() -> executeCallback(responsePacket.futureId, payload)));
    }
}