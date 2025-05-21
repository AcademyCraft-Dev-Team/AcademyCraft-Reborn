package org.academy.api.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.AbstractFutureManager;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SFuturePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.academy.api.common.network.packet.S2CFuturePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class FutureManagerClient extends AbstractFutureManager<ClientPacketListener, S2CFuturePacket> {
    private static final FutureManagerClient INSTANCE = new FutureManagerClient();

    private FutureManagerClient() {
    }

    public static void init() {
        INSTANCE.registerSelf();
    }

    public static <T_REQ_PAYLOAD extends FutureRequestPayload, T_RESP> void registerFutureProcessor(
            Class<T_REQ_PAYLOAD> requestPayloadClass,
            BiFunction<T_REQ_PAYLOAD, ClientPacketListener, T_RESP> processor) {
        INSTANCE.registerProcessorInternal(requestPayloadClass, processor);
    }

    public static <T_REQ_PAYLOAD extends FutureRequestPayload, T_RESP> void sendFutureRequestToServer(
            @NotNull Class<T_REQ_PAYLOAD> requestPayloadClass,
            @NotNull Consumer<T_RESP> handler,
            Object... values) {
        INSTANCE.sendRequestInternal(null, requestPayloadClass, handler, values);
    }

    @SubscribePacket
    public static void handleS2CFuturePacket(S2CFuturePacket packet) {
        INSTANCE.handleIncomingPacketInternal(packet);
    }

    @Override
    protected void sendResponsePacket(ClientPacketListener listener, int futureId, Object responseData) {
        listener.send(new C2SPacket(new C2SFuturePacket(futureId, responseData)));
    }

    @Override
    protected <P extends FutureRequestPayload> void dispatchRequestPacket(
            @Nullable ClientPacketListener specificListener,
            int futureId,
            Class<P> requestPayloadClass,
            FriendlyByteBuf payloadBuffer) {
        C2SFuturePacket futureRequest = new C2SFuturePacket(futureId, requestPayloadClass, payloadBuffer);
        NetworkSystemClient.sendPacket(new C2SPacket(futureRequest));
    }
}