package org.academy.api.client.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.annotation.SubscribePacket;
import org.academy.api.common.network.future.AbstractFutureManager;
import org.academy.api.common.network.future.packet.FutureRequestPacket;
import org.academy.api.common.network.future.packet.FutureResponsePacket;
import org.academy.api.common.network.future.packet.RequestPacket;
import org.academy.api.common.network.future.packet.ResponsePacket;

import java.util.function.Consumer;

public class FutureManagerClient extends AbstractFutureManager {
    public FutureManagerClient() {
    }

    public <
            RES_L extends ClientboundPacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_L extends ServerboundPacketListener,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            >
    void sendRequestToServer(REQ_P requestPacket, Consumer<RES_P> callback, long timeoutMillis) {
        var futureId = createPendingFuture(requestPacket.getResponsePacketType(), callback, timeoutMillis);
        if (futureId == -1) return;
        var requestTypeId = requestPacket.getPacketType().getPacketId();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        requestPacket.getPacketType().codec().encode(buffer, requestPacket);

        var payload = new byte[buffer.readableBytes()];
        buffer.readBytes(payload);

        var packet = new FutureRequestPacket<ServerGamePacketListenerImpl>(futureId, requestTypeId, payload);
        AcademyCraftClient.sendPacket(packet);
    }

    public <
            RES_L extends ClientboundPacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_L extends ServerboundPacketListener,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            >
    void sendRequestToServer(REQ_P requestPacket, Consumer<RES_P> callback) {
        sendRequestToServer(requestPacket, callback, DEFAULT_TIMEOUT_MS);
    }

    @SubscribePacket
    public <
            RES_P extends ResponsePacket<ServerGamePacketListenerImpl, RES_P>,
            REQ_P extends RequestPacket<ClientGamePacketListener, REQ_P, ServerGamePacketListenerImpl, RES_P>
            > void handleFutureRequestFromServer(FutureRequestPacket<ClientGamePacketListener> futureRequestPacket) {
        super.<ClientGamePacketListener, FutureRequestPacket<ClientGamePacketListener>, ServerGamePacketListenerImpl, RES_P, REQ_P>handleRequest(
                futureRequestPacket, futureRequestPacket.getPacketListener(), response -> {
                    var responseTypeId = response.getPacketType().getPacketId();
                    var responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    response.getPacketType().codec().encode(responseBuffer, response);

                    var payload = new byte[responseBuffer.readableBytes()];
                    responseBuffer.readBytes(payload);

                    var responsePkt = new FutureResponsePacket<ServerGamePacketListenerImpl>(
                            futureRequestPacket.getFutureId(), responseTypeId, payload
                    );
                    AcademyCraftClient.sendPacket(responsePkt);
                }
        );
    }

    @SubscribePacket
    public void handleFutureResponseFromServer(FutureResponsePacket<ClientGamePacketListener> responsePacket) {
        handleResponse(responsePacket, payload ->
                Minecraft.getInstance().execute(
                        () -> executeCallback(
                                responsePacket.getFutureId(), payload
                        )
                )
        );
    }
}