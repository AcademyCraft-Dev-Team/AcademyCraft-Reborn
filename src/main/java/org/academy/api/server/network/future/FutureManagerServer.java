package org.academy.api.server.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.future.AbstractFutureManager;
import org.academy.api.common.network.future.packet.FutureRequestPacket;
import org.academy.api.common.network.future.packet.FutureResponsePacket;
import org.academy.api.common.network.future.packet.RequestPacket;
import org.academy.api.common.network.future.packet.ResponsePacket;
import org.academy.api.common.network.packet.S2CPacket;

import java.util.function.Consumer;

public class FutureManagerServer extends AbstractFutureManager {
    public FutureManagerServer() {
    }

    public <
            RES_L extends ClientboundPacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_L extends ServerboundPacketListener,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            >
    void sendRequestToClient(ServerPlayer player, REQ_P requestPacket, Consumer<RES_P> callback, long timeoutMillis) {
        var futureId = createPendingFuture(requestPacket.getResponsePacketType(), callback, timeoutMillis);
        if (futureId == -1) return;
        var requestTypeId = requestPacket.getPacketType().getPacketId();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        requestPacket.getPacketType().getCodec().encode(buffer, requestPacket);

        var payload = new byte[buffer.readableBytes()];
        buffer.readBytes(payload);

        var packet = new FutureRequestPacket<ClientGamePacketListener>(futureId, requestTypeId, payload);
        player.connection.send(new S2CPacket(packet));
    }

    public <
            RES_L extends ClientboundPacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_L extends ServerboundPacketListener,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            >
    void sendRequestToClient(ServerPlayer player, REQ_P requestPacket, Consumer<RES_P> callback) {
        sendRequestToClient(player, requestPacket, callback, DEFAULT_TIMEOUT_MS);
    }

    @SubscribePacket
    public <
            RES_P extends ResponsePacket<ClientGamePacketListener, RES_P>,
            REQ_P extends RequestPacket<ServerGamePacketListenerImpl, REQ_P, ClientGamePacketListener, RES_P>
            > void handleFutureRequestFromClient(FutureRequestPacket<ServerGamePacketListenerImpl> futureRequestPacket) {
        var packetListener = futureRequestPacket.getPacketListener();
        var player = packetListener.getPlayer();

        super.<ServerGamePacketListenerImpl, FutureRequestPacket<ServerGamePacketListenerImpl>, ClientGamePacketListener, RES_P, REQ_P>handleRequest(
                futureRequestPacket, futureRequestPacket.getPacketListener(), response -> {
                    var responseTypeId = response.getPacketType().getPacketId();
                    var responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    response.getPacketType().getCodec().encode(responseBuffer, response);

                    var payload = new byte[responseBuffer.readableBytes()];
                    responseBuffer.readBytes(payload);

                    var responsePkt = new FutureResponsePacket<ClientGamePacketListener>(
                            futureRequestPacket.getFutureId(), responseTypeId, payload
                    );
                    player.connection.send(new S2CPacket(responsePkt));
                }
        );
    }

    @SubscribePacket
    public void handleFutureResponseFromClient(FutureResponsePacket<ServerGamePacketListenerImpl> responsePacket) {
        handleResponse(responsePacket, payload ->
                responsePacket.getPacketListener().server.execute(
                        () -> executeCallback(
                                responsePacket.getFutureId(), payload
                        )
                )
        );
    }
}