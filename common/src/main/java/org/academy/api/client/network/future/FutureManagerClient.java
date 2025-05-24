package org.academy.api.client.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraft;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.asm.InstanceCreator;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.future.*;
import org.academy.api.common.network.future.asm.IPayloadHandlerInvoker;
import org.academy.api.common.network.future.asm.PayloadHandlerInvokerFactory;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.FutureRequestPacket;
import org.academy.api.common.network.packet.FutureResponsePacket;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FutureManagerClient {
    private static final Map<Integer, PendingFutureInfo> pendingFutures = new HashMap<>();
    private static final BitSet usedFutureIds = new BitSet();
    private static final Map<Integer, IPayloadHandlerInvoker> requestHandlers = new HashMap<>();

    private record PendingFutureInfo(Consumer<?> callback, int expectedResponsePayloadId) {
    }

    private FutureManagerClient() {
    }

    public static void init() {
        NetworkSystemClient.registerPacketListener(FutureManagerClient.class);
    }

    private static int generateFutureId() {
        int id = usedFutureIds.nextClearBit(0);
        if (id >= Integer.MAX_VALUE / 2) {
            usedFutureIds.clear();
            id = 0;
        }
        usedFutureIds.set(id);
        return id;
    }

    private static PendingFutureInfo getAndRemovePendingFuture(int futureId) {
        PendingFutureInfo info = pendingFutures.remove(futureId);
        if (info != null) {
            usedFutureIds.clear(futureId);
        }
        return info;
    }

    @SuppressWarnings({"unchecked", "DuplicatedCode"})
    public static void registerPayloadHandler(Object owner) {
        Class<?> clazz = owner instanceof Class ? (Class<?>) owner : owner.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribePayload.class)) {
                boolean isStaticMethod = Modifier.isStatic(method.getModifiers());
                if (!isStaticMethod && owner instanceof Class) {
                    AcademyCraft.LOGGER.warn("Client: Cannot register non-static @SubscribePayload method {} from a Class object. Provide an instance.", method);
                    continue;
                }
                if (isStaticMethod && !(owner instanceof Class)) {
                    AcademyCraft.LOGGER.warn("Client: Should register static @SubscribePayload method {} using its Class object.", method);
                }

                Parameter[] parameters = method.getParameters();
                if (parameters.length != 1 || !IRequestPayload.class.isAssignableFrom(parameters[0].getType())) {
                    AcademyCraft.LOGGER.error("Client: Method {} annotated with @SubscribePayload must have exactly one IRequestPayload parameter.", method);
                    continue;
                }
                if (!IResponsePayload.class.isAssignableFrom(method.getReturnType()) || method.getReturnType() == void.class) {
                    AcademyCraft.LOGGER.error("Client: Method {} annotated with @SubscribePayload must return a type implementing IResponsePayload.", method);
                    continue;
                }

                Class<? extends IRequestPayload<?, ?>> requestType = (Class<? extends IRequestPayload<?, ?>>) parameters[0].getType();
                Class<? extends IResponsePayload> responseType = (Class<? extends IResponsePayload>) method.getReturnType();

                FutureManager.registerPayloadType(requestType);
                FutureManager.registerPayloadType(responseType);

                IPayloadHandlerInvoker invoker = isStaticMethod ?
                        PayloadHandlerInvokerFactory.createStaticInvoker(method, requestType, responseType) :
                        PayloadHandlerInvokerFactory.createInstanceInvoker(method, requestType, responseType, owner);

                int requestTypeId = FutureManager.getPayloadId(requestType);

                if (requestHandlers.containsKey(requestTypeId) && AcademyCraft.DEBUG_MODE) {
                    AcademyCraft.LOGGER.warn("Client: Replacing payload handler for request type ID: {}", requestTypeId);
                }
                requestHandlers.put(requestTypeId, invoker);
            }
        }
    }

    @SuppressWarnings("DuplicatedCode")
    public static <T_RESP extends IResponsePayload, T_REQ_LISTENER extends PacketListener, REQUEST extends IRequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToServer(
            REQUEST requestPayload, Consumer<T_RESP> callback) {
        Class<T_RESP> responseClass = requestPayload.getExpectedResponseType();
        FutureManager.registerPayloadType(requestPayload.getClass());
        FutureManager.registerPayloadType(responseClass);

        int futureId = generateFutureId();
        int requestTypeId = FutureManager.getPayloadId(requestPayload.getClass());
        int expectedResponsePayloadId = FutureManager.getPayloadId(responseClass);
        pendingFutures.put(futureId, new PendingFutureInfo(callback, expectedResponsePayloadId));
        FriendlyByteBuf payloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        requestPayload.write(payloadBuffer);

        FutureRequestPacket<ServerGamePacketListenerImpl> packet = new FutureRequestPacket<>(futureId, requestTypeId, payloadBuffer);
        NetworkSystemClient.sendPacket(new C2SPacket(packet));
    }

    @SubscribePacket
    public static void handleFutureRequestFromServer(FutureRequestPacket<ClientPacketListener> requestPacket) {
        Supplier<ClientPacketListener> supplier = requestPacket.packetListenerSupplier;
        if (supplier == null || supplier.get() == null) return;

        IPayloadHandlerInvoker invoker = requestHandlers.get(requestPacket.payloadTypeId);
        if (invoker == null) {
            AcademyCraft.LOGGER.error("Client: No handler for request payload ID {}", requestPacket.payloadTypeId);
            return;
        }

        InstanceCreator<IRequestPayload<ClientPacketListener, ?>> creator = FutureManager.getPayloadCreator(requestPacket.payloadTypeId);
        if (creator == null) {
            AcademyCraft.LOGGER.error("Client: No creator for request payload ID {}", requestPacket.payloadTypeId);
            return;
        }
        IRequestPayload<ClientPacketListener, ?> requestPayload = creator.create();
        requestPayload.read(requestPacket.payloadData);
        requestPayload.packetListenerSupplier = supplier;

        IResponsePayload responsePayload = invoker.invoke(requestPayload);

        if (responsePayload != null) {
            int responseTypeId = FutureManager.getPayloadId(responsePayload.getClass());
            FriendlyByteBuf responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
            responsePayload.write(responseBuffer);
            FutureResponsePacket<ServerGamePacketListenerImpl> responsePkt = new FutureResponsePacket<>(requestPacket.futureId, responseTypeId, responseBuffer);
            NetworkSystemClient.sendPacket(new C2SPacket(responsePkt));
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribePacket
    public static void handleFutureResponseFromServer(FutureResponsePacket<ClientPacketListener> responsePacket) {
        PendingFutureInfo pendingInfo = getAndRemovePendingFuture(responsePacket.futureId);

        InstanceCreator<? extends IPayload> creator = FutureManager.getPayloadCreator(responsePacket.payloadTypeId);
        IPayload responsePayload = creator.create();
        responsePayload.read(responsePacket.payloadData);

        Consumer<IPayload> callback = (Consumer<IPayload>) pendingInfo.callback();
        callback.accept(responsePayload);
    }
}