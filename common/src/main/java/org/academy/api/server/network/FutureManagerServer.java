package org.academy.api.server.network;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraft;
import org.academy.api.common.network.C2SFuturePacket;
import org.academy.api.common.network.S2CFuturePacket;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.network.packet.FutureRequestPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class FutureManagerServer {
    private static final Map<Class<? extends FutureRequestPayload>, BiFunction<? extends FutureRequestPayload, ServerGamePacketListenerImpl, Object>> FUTURE_REQUEST_PROCESSORS = new HashMap<>();
    private static final Map<Class<? extends FutureRequestPayload>, Integer> PAYLOAD_CLASS_TO_ID = new HashMap<>();
    private static final Map<Integer, Class<? extends FutureRequestPayload>> ID_TO_PAYLOAD_CLASS = new HashMap<>();
    private static final AtomicInteger nextPayloadClassId = new AtomicInteger(0);

    @SuppressWarnings("unchecked")
    public static <T_REQ_PAYLOAD extends FutureRequestPayload, T_RESP> void registerFutureProcessor(
            Class<T_REQ_PAYLOAD> requestPayloadClass,
            BiFunction<T_REQ_PAYLOAD, ServerGamePacketListenerImpl, T_RESP> processor) {

        FUTURE_REQUEST_PROCESSORS.put(requestPayloadClass, (BiFunction<FutureRequestPayload, ServerGamePacketListenerImpl, Object>) processor);
        if (!PAYLOAD_CLASS_TO_ID.containsKey(requestPayloadClass)) {
            int id = nextPayloadClassId.getAndIncrement();
            PAYLOAD_CLASS_TO_ID.put(requestPayloadClass, id);
            ID_TO_PAYLOAD_CLASS.put(id, requestPayloadClass);
        }
    }

    @SubscribePacket
    public static void handleFutureRequest(C2SFuturePacket packet) {
        ServerGamePacketListenerImpl serverListener = packet.packetListenerSupplier.get();

        if (packet.isResponse) {
            return;
        }

        Class<? extends FutureRequestPayload> requestPayloadClass = ID_TO_PAYLOAD_CLASS.get(packet.payloadTypeId);

        if (requestPayloadClass == null) {
            for (Map.Entry<Class<? extends FutureRequestPayload>, Integer> entry : PAYLOAD_CLASS_TO_ID.entrySet()) {
                if (entry.getKey().getName().hashCode() == packet.payloadTypeId) {
                    requestPayloadClass = entry.getKey();
                    break;
                }
            }
            if (requestPayloadClass == null) {
                AcademyCraft.LOGGER.error("Received FutureRequestPacket with unknown payloadTypeId: {}", packet.payloadTypeId);
                return;
            }
        }

        BiFunction<? extends FutureRequestPayload, ServerGamePacketListenerImpl, Object> processor = FUTURE_REQUEST_PROCESSORS.get(requestPayloadClass);

        if (processor != null) {
            try {
                FutureRequestPayload payloadInstance = requestPayloadClass.getDeclaredConstructor().newInstance();
                payloadInstance.readPayload(packet.payloadData);

                @SuppressWarnings("unchecked")
                BiFunction<FutureRequestPayload, ServerGamePacketListenerImpl, Object> castedProcessor =
                        (BiFunction<FutureRequestPayload, ServerGamePacketListenerImpl, Object>) processor;

                Object result = castedProcessor.apply(payloadInstance, serverListener);

                if (result != null) {
                    sendResult(serverListener, packet.futureId, result);
                }
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Error processing future request for payload class {}: {}", requestPayloadClass.getName(), e.getMessage(), e);
            }
        } else {
            AcademyCraft.LOGGER.warn("No future processor registered for payload class: {}", requestPayloadClass.getName());
        }
    }

    public static <T> void sendResult(ServerGamePacketListenerImpl listener, int futureId, T value) {
        listener.send(new S2CPacket(new S2CFuturePacket(futureId, value)));
    }

    public static void register() {
        NetworkSystem.registerPacketListener(FutureManagerServer.class);
    }

    private FutureManagerServer() {
    }
}