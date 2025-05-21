package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.academy.AcademyCraft;
import org.academy.api.common.asm.InstanceCreatorFactory;
import org.academy.api.common.asm.InstanceCreator;
import org.academy.api.common.network.packet.FuturePacket;
import org.academy.api.common.network.packet.FutureRequestPayload;
import org.academy.api.common.util.FriendlyByteBufUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public abstract class AbstractFutureManager<L extends PacketListener, IN_PACKET extends FuturePacket<L>> {
    protected static final Map<Class<? extends FutureRequestPayload>, Integer> PAYLOAD_CLASS_TO_ID = new HashMap<>();
    protected static final Map<Integer, Class<? extends FutureRequestPayload>> ID_TO_PAYLOAD_CLASS = new HashMap<>();
    protected static final AtomicInteger nextPayloadClassId = new AtomicInteger(0);
    protected static final Map<Class<? extends FutureRequestPayload>, InstanceCreator<? extends FutureRequestPayload>> PAYLOAD_INSTANCE_CREATORS = new HashMap<>();

    protected final Map<Integer, Consumer<?>> pendingFutures = new HashMap<>();
    protected final BitSet usedFutureIds = new BitSet();
    protected final Map<Class<? extends FutureRequestPayload>, BiFunction<? extends FutureRequestPayload, L, ?>> requestProcessors = new HashMap<>();

    protected static <P extends FutureRequestPayload> void ensurePayloadRegistered(Class<P> payloadClass) {
        if (!PAYLOAD_CLASS_TO_ID.containsKey(payloadClass)) {
            synchronized (PAYLOAD_CLASS_TO_ID) {
                if (!PAYLOAD_CLASS_TO_ID.containsKey(payloadClass)) {
                    int id = nextPayloadClassId.getAndIncrement();
                    PAYLOAD_CLASS_TO_ID.put(payloadClass, id);
                    ID_TO_PAYLOAD_CLASS.put(id, payloadClass);
                    PAYLOAD_INSTANCE_CREATORS.put(payloadClass, InstanceCreatorFactory.createInstanceCreator(payloadClass));
                }
            }
        }
    }

    protected <P extends FutureRequestPayload, T_RESP> void registerProcessorInternal(
            Class<P> requestPayloadClass,
            BiFunction<P, L, T_RESP> processor) {
        ensurePayloadRegistered(requestPayloadClass);
        this.requestProcessors.put(requestPayloadClass, processor);
    }

    @SuppressWarnings("unchecked")
    protected void handleIncomingPacketInternal(@NotNull IN_PACKET packet) {
        L listener = packet.packetListenerSupplier.get();
        if (listener == null) {
            AcademyCraft.LOGGER.error("PacketListenerSupplier returned null for packet: {}", packet.getClass().getName());
            return;
        }

        if (packet.isResponse) {
            handleResponseInternal(packet.futureId, packet.payloadTypeId, packet.payloadData);
        } else {
            Class<? extends FutureRequestPayload> requestPayloadClass = ID_TO_PAYLOAD_CLASS.get(packet.payloadTypeId);

            if (requestPayloadClass == null) {
                for (Map.Entry<Class<? extends FutureRequestPayload>, Integer> entry : PAYLOAD_CLASS_TO_ID.entrySet()) {
                    if (entry.getKey().getName().hashCode() == packet.payloadTypeId) {
                        requestPayloadClass = entry.getKey();
                        break;
                    }
                }
                if (requestPayloadClass == null) {
                    AcademyCraft.LOGGER.error("Received FuturePacket with unknown payloadTypeId for request: {}", packet.payloadTypeId);
                    return;
                }
            }

            BiFunction<? extends FutureRequestPayload, L, ?> processor = requestProcessors.get(requestPayloadClass);
            if (processor != null) {
                try {
                    InstanceCreator<? extends FutureRequestPayload> creator = PAYLOAD_INSTANCE_CREATORS.get(requestPayloadClass);
                    if (creator == null) {
                        AcademyCraft.LOGGER.error("No creator found for FutureRequestPayload class: {}", requestPayloadClass.getName());
                        return;
                    }
                    FutureRequestPayload payloadInstance = creator.create();
                    payloadInstance.readPayload(packet.payloadData);

                    Object result = ((BiFunction<FutureRequestPayload, L, ?>) processor).apply(payloadInstance, listener);

                    if (result != null) {
                        sendResponsePacket(listener, packet.futureId, result);
                    }
                } catch (Throwable e) {
                    AcademyCraft.LOGGER.error("Error processing future request for payload class {}: {}", requestPayloadClass.getName(), e.getMessage(), e);
                }
            } else {
                AcademyCraft.LOGGER.warn("No future processor registered for payload class: {}", requestPayloadClass.getName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleResponseInternal(int futureId, int payloadTypeId, FriendlyByteBuf payloadData) {
        Consumer<Object> handler = (Consumer<Object>) pendingFutures.remove(futureId);
        usedFutureIds.clear(futureId);

        if (handler != null) {
            FriendlyByteBufDeserializer<?> deserializer = FriendlyByteBufDeserializers.getRequiredDeserializer(payloadTypeId);
            Object value = deserializer.deserialize(payloadData);
            handler.accept(value);
        } else {
            AcademyCraft.LOGGER.warn("Received response for unknown or timed-out futureId: {}", futureId);
        }
    }

    protected abstract void sendResponsePacket(L listener, int futureId, Object responseData);

    protected <P extends FutureRequestPayload, T_RESP> void sendRequestInternal(
            @Nullable L specificListener,
            Class<P> requestPayloadClass,
            Consumer<T_RESP> handler,
            Object... values) {

        ensurePayloadRegistered(requestPayloadClass);

        int futureId = usedFutureIds.nextClearBit(0);
        usedFutureIds.set(futureId);
        pendingFutures.put(futureId, handler);

        FriendlyByteBuf payloadBuffer = FriendlyByteBufUtil.autoSerializable(values);
        dispatchRequestPacket(specificListener, futureId, requestPayloadClass, payloadBuffer);
    }

    protected abstract <P extends FutureRequestPayload> void dispatchRequestPacket(
            @Nullable L specificListener,
            int futureId,
            Class<P> requestPayloadClass,
            FriendlyByteBuf payloadBuffer
    );

    public void registerSelf() {
        NetworkSystem.registerPacketListener(this.getClass());
    }
}