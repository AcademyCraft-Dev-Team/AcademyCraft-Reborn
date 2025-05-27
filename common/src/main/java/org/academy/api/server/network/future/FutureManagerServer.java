package org.academy.api.server.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraft;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.future.*;
import org.academy.api.common.network.future.asm.IPayloadHandlerInvoker;
import org.academy.api.common.network.future.asm.PayloadHandlerInvokerFactory;
import org.academy.api.common.network.packet.FutureRequestPacket;
import org.academy.api.common.network.packet.FutureResponsePacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.server.network.NetworkSystemServer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class FutureManagerServer {
    private final Map<Integer, PendingFutureInfo> pendingFutures;
    private final BitSet usedFutureIds;
    private final Map<Integer, IPayloadHandlerInvoker> requestHandlers;
    private final FutureManager futureManager;
    private final NetworkSystemServer networkSystemServer;

    private record PendingFutureInfo(Consumer<?> callback, int expectedResponsePayloadId) {
    }

    public FutureManagerServer(FutureManager futureManager, NetworkSystemServer networkSystemServer) {
        this.futureManager = futureManager;
        this.networkSystemServer = networkSystemServer;
        this.pendingFutures = new HashMap<>();
        this.usedFutureIds = new BitSet();
        this.requestHandlers = new HashMap<>();
    }

    public void clear() {
        this.pendingFutures.clear();
        this.usedFutureIds.clear();
        this.requestHandlers.clear();
        this.networkSystemServer.registerPacketListener(this);
    }

    private int generateFutureId() {
        int id = this.usedFutureIds.nextClearBit(0);
        if (id >= Integer.MAX_VALUE / 2) {
            this.usedFutureIds.clear();
            id = 0;
        }
        this.usedFutureIds.set(id);
        return id;
    }

    private PendingFutureInfo getAndRemovePendingFuture(int futureId) {
        PendingFutureInfo info = this.pendingFutures.remove(futureId);
        if (info != null) {
            this.usedFutureIds.clear(futureId);
        }
        return info;
    }

    @SuppressWarnings({"unchecked", "DuplicatedCode"})
    public void registerPayloadHandler(Object owner) {
        Class<?> clazz = owner instanceof Class ? (Class<?>) owner : owner.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(HandlePayload.class)) {
                boolean isStaticMethod = Modifier.isStatic(method.getModifiers());
                if (!isStaticMethod && owner instanceof Class) {
                    AcademyCraft.LOGGER.warn("Server: Cannot register non-static @SubscribePayload method {} from a Class object. Provide an instance.", method);
                    continue;
                }
                if (isStaticMethod && !(owner instanceof Class)) {
                    AcademyCraft.LOGGER.warn("Server: Should register static @SubscribePayload method {} using its Class object.", method);
                }

                Parameter[] parameters = method.getParameters();
                if (parameters.length != 1 || !IRequestPayload.class.isAssignableFrom(parameters[0].getType())) {
                    AcademyCraft.LOGGER.error("Server: Method {} annotated with @SubscribePayload must have exactly one IRequestPayload parameter.", method);
                    continue;
                }
                if (!IResponsePayload.class.isAssignableFrom(method.getReturnType()) || method.getReturnType() == void.class) {
                    AcademyCraft.LOGGER.error("Server: Method {} annotated with @SubscribePayload must return a type implementing IResponsePayload.", method);
                    continue;
                }

                Class<? extends IRequestPayload<?, ?>> requestType = (Class<? extends IRequestPayload<?, ?>>) parameters[0].getType();
                Class<? extends IResponsePayload> responseType = (Class<? extends IResponsePayload>) method.getReturnType();

                IPayloadHandlerInvoker invoker = isStaticMethod ?
                        PayloadHandlerInvokerFactory.createStaticInvoker(method, requestType, responseType) :
                        PayloadHandlerInvokerFactory.createInstanceInvoker(method, requestType, responseType, owner);

                int requestTypeId = this.futureManager.getPayloadId(requestType);

                if (this.requestHandlers.containsKey(requestTypeId) && AcademyCraft.DEBUG_MODE) {
                    AcademyCraft.LOGGER.warn("Server: Replacing payload handler for request type ID: {}", requestTypeId);
                }
                this.requestHandlers.put(requestTypeId, invoker);
            }
        }
    }

    @SuppressWarnings("DuplicatedCode")
    public <T_RESP extends IResponsePayload, T_REQ_LISTENER extends PacketListener, REQUEST extends IRequestPayload<T_REQ_LISTENER, T_RESP>> void sendRequestToClient(
            ServerPlayer player, REQUEST requestPayload, Consumer<T_RESP> callback) {
        Class<T_RESP> responseClass = requestPayload.getExpectedResponseType();

        int futureId = generateFutureId();
        int requestTypeId = this.futureManager.getPayloadId(requestPayload.getClass());
        int expectedResponsePayloadId = this.futureManager.getPayloadId(responseClass);
        this.pendingFutures.put(futureId, new PendingFutureInfo(callback, expectedResponsePayloadId));
        FriendlyByteBuf payloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        requestPayload.write(payloadBuffer);

        FutureRequestPacket<ClientPacketListener> packet = new FutureRequestPacket<>(futureId, requestTypeId, payloadBuffer);
        player.connection.send(new S2CPacket(packet));
    }

    @SubscribePacket
    public void handleFutureRequestFromClient(FutureRequestPacket<ServerGamePacketListenerImpl> requestPacket) {
        Supplier<ServerGamePacketListenerImpl> supplier = requestPacket.packetListenerSupplier;
        ServerGamePacketListenerImpl listener = supplier.get();
        ServerPlayer player = listener.player;

        IPayloadHandlerInvoker invoker = this.requestHandlers.get(requestPacket.payloadTypeId);
        if (invoker == null) {
            AcademyCraft.LOGGER.error("Server: No handler for request payload ID {}", requestPacket.payloadTypeId);
            return;
        }
        Function<PacketListener, ? extends IRequestPayload<ServerGamePacketListenerImpl, ?>> factory = this.futureManager.getPayloadFactory(requestPacket.payloadTypeId);
        if (factory == null) {
            AcademyCraft.LOGGER.error("Server: No factory for request payload ID {}", requestPacket.payloadTypeId);
            return;
        }
        IRequestPayload<ServerGamePacketListenerImpl, ?> requestPayload = factory.apply(listener);
        if (requestPayload.packetListenerSupplier == null) {
            requestPayload.packetListenerSupplier = supplier;
        }
        requestPayload.read(requestPacket.payloadData);

        IResponsePayload responsePayload = invoker.invoke(requestPayload);
        if (responsePayload != null) {
            int responseTypeId = this.futureManager.getPayloadId(responsePayload.getClass());
            FriendlyByteBuf responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
            responsePayload.write(responseBuffer);
            FutureResponsePacket<ClientPacketListener> responsePkt = new FutureResponsePacket<>(requestPacket.futureId, responseTypeId, responseBuffer);
            player.connection.send(new S2CPacket(responsePkt));
        }
    }

    @SubscribePacket
    public void handleFutureResponseFromClient(FutureResponsePacket<ServerGamePacketListenerImpl> responsePacket) {
        Supplier<ServerGamePacketListenerImpl> supplier = responsePacket.packetListenerSupplier;
        if (supplier == null || supplier.get() == null) return;

        PendingFutureInfo pendingInfo = getAndRemovePendingFuture(responsePacket.futureId);
        if (pendingInfo == null) {
            AcademyCraft.LOGGER.warn("Server: Received response for unknown/timed-out futureId: {}", responsePacket.futureId);
            return;
        }

        if (pendingInfo.expectedResponsePayloadId() != -1 && pendingInfo.expectedResponsePayloadId() != responsePacket.payloadTypeId) {
            AcademyCraft.LOGGER.error("Server: Mismatched response. Expected ID {}, Got ID {}", pendingInfo.expectedResponsePayloadId(), responsePacket.payloadTypeId);
            return;
        }

        Function<PacketListener, ? extends IPayload> factory = this.futureManager.getPayloadFactory(responsePacket.payloadTypeId);
        if (factory == null) {
            AcademyCraft.LOGGER.error("Server: No factory for response payload ID {}", responsePacket.payloadTypeId);
            return;
        }

        try {
            IPayload responsePayload = factory.apply(null);
            responsePayload.read(responsePacket.payloadData);

            @SuppressWarnings("unchecked")
            Consumer<IPayload> unsafeConsumer = (Consumer<IPayload>) pendingInfo.callback();
            if (unsafeConsumer != null) {
                unsafeConsumer.accept(responsePayload);
            }
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Server: Error processing response for futureId {}: {}", responsePacket.futureId, e.getMessage(), e);
        }
    }
}