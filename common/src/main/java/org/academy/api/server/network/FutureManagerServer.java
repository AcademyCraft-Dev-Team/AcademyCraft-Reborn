package org.academy.api.server.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.AbstractFutureManager;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SFuturePacket;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.academy.api.common.network.packet.S2CFuturePacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class FutureManagerServer extends AbstractFutureManager<ServerGamePacketListenerImpl, C2SFuturePacket> {
    private static final FutureManagerServer INSTANCE = new FutureManagerServer();

    private FutureManagerServer() {}

    public static void init() {
        INSTANCE.registerSelf();
    }

    public static <T_REQ_PAYLOAD extends FutureRequestPayload, T_RESP> void registerFutureProcessor(
            Class<T_REQ_PAYLOAD> requestPayloadClass,
            BiFunction<T_REQ_PAYLOAD, ServerGamePacketListenerImpl, T_RESP> processor) {
        INSTANCE.registerProcessorInternal(requestPayloadClass, processor);
    }

    public static <T_REQ_PAYLOAD extends FutureRequestPayload, T_RESP> void sendFutureRequestToClient(
            @NotNull ServerGamePacketListenerImpl listener,
            @NotNull Class<T_REQ_PAYLOAD> requestPayloadClass,
            @NotNull Consumer<T_RESP> handler,
            Object... values) {
        INSTANCE.sendRequestInternal(listener, requestPayloadClass, handler, values);
    }

    @SubscribePacket
    public static void handleC2SFuturePacket(C2SFuturePacket packet) {
        INSTANCE.handleIncomingPacketInternal(packet);
    }

    @Override
    protected void sendResponsePacket(ServerGamePacketListenerImpl listener, int futureId, Object responseData) {
        listener.send(new S2CPacket(new S2CFuturePacket(futureId, responseData)));
    }

    @Override
    protected <P extends FutureRequestPayload> void dispatchRequestPacket(
            @Nullable ServerGamePacketListenerImpl specificListener,
            int futureId,
            Class<P> requestPayloadClass,
            FriendlyByteBuf payloadBuffer) {
        if (specificListener == null) {
            return;
        }
        S2CFuturePacket futureRequest = new S2CFuturePacket(futureId, requestPayloadClass, payloadBuffer);
        specificListener.send(new S2CPacket(futureRequest));
    }
}