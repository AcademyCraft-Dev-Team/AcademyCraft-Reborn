package org.academy.api.client.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.*;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class FutureManagerClient {
    private static final Map<Integer, Consumer<?>> FUTURES = new HashMap<>();
    private static final BitSet USED_IDS = new BitSet();

    public static <T_REQ_PAYLOAD extends FutureRequestPayload, T_RESP> void sendFuturePacket(
            @NotNull Class<T_REQ_PAYLOAD> requestPayloadClass,
            @NotNull Consumer<T_RESP> handler,
            Object... constructorArgsForPayload) {

        int futureId = USED_IDS.nextClearBit(0);
        USED_IDS.set(futureId);
        FUTURES.put(futureId, handler);

        FriendlyByteBuf payloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Class<?>[] argTypes = Stream.of(constructorArgsForPayload)
                    .map(Object::getClass)
                    .toArray(Class<?>[]::new);

            Constructor<T_REQ_PAYLOAD> constructor = requestPayloadClass.getDeclaredConstructor(argTypes);
            T_REQ_PAYLOAD payloadInstance = constructor.newInstance(constructorArgsForPayload);
            payloadInstance.writePayload(payloadBuffer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create or write FutureRequestPayload: " + requestPayloadClass.getName() + " with args " + Arrays.toString(constructorArgsForPayload), e);
        }

        C2SFuturePacket futureRequest = new C2SFuturePacket(futureId, requestPayloadClass, payloadBuffer);
        NetworkSystemClient.sendPacket(new C2SPacket(futureRequest));
    }

    @SubscribePacket
    @SuppressWarnings("unchecked")
    public static void handleFutureResponse(S2CFuturePacket packet) {
        if (!packet.isResponse) {
            return;
        }

        int futureId = packet.futureId;
        Consumer<Object> handler = (Consumer<Object>) FUTURES.remove(futureId);
        USED_IDS.clear(futureId);

        if (handler != null) {
            FriendlyByteBufDeserializer<?> deserializer = FriendlyByteBufDeserializers.getRequiredDeserializer(packet.payloadTypeId);
            Object value = deserializer.deserialize(packet.payloadData);
            handler.accept(value);
        }
    }

    public static void register() {
        NetworkSystem.registerPacketListener(FutureManagerClient.class);
    }

    private FutureManagerClient() {
    }
}