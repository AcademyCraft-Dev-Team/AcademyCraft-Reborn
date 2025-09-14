package org.academy.api.common.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.academy.AcademyCraft;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.future.asm.IFutureHandlerInvoker;
import org.academy.api.common.network.future.asm.PayloadHandlerInvokerFactory;
import org.academy.api.common.network.future.packet.FuturePacket;
import org.academy.api.common.network.future.packet.RequestPacket;
import org.academy.api.common.network.future.packet.ResponsePacket;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class AbstractFutureManager {
    protected final Map<Integer, PendingFutureInfo> pendingFutures = new ConcurrentHashMap<>();
    protected final Map<Integer, IFutureHandlerInvoker<?, ?, ?, ?>> requestHandlers = new ConcurrentHashMap<>();
    private final AtomicInteger nextFutureId = new AtomicInteger(0);
    protected static final long DEFAULT_TIMEOUT_MS = 60000;

    protected record PendingFutureInfo(Consumer<?> callback, int expectedResponsePacketId, long expireTime) {
    }

    protected AbstractFutureManager() {
        AcademyCraft.executorService.scheduleAtFixedRate(this::cleanupTimedOutFutures, 1, 1, TimeUnit.SECONDS);
    }

    public void clear() {
        this.pendingFutures.clear();
        this.requestHandlers.clear();
    }

    protected int generateFutureId() {
        return nextFutureId.getAndIncrement();
    }

    protected <T_RESP extends ResponsePacket<?, T_RESP>> int createPendingFuture(PacketType<?, T_RESP> responsePayloadType, Consumer<T_RESP> callback, long timeoutMillis) {
        int futureId = generateFutureId();
        int expectedResponsePayloadId = responsePayloadType.getPacketId();
        if (expectedResponsePayloadId == -1) {
            AcademyCraft.LOGGER.error("FutureManager: Response payload type {} is not registered.", responsePayloadType.packetClass().getName());
            return -1;
        }
        long expireTime = System.currentTimeMillis() + timeoutMillis;
        pendingFutures.put(futureId, new PendingFutureInfo(callback, expectedResponsePayloadId, expireTime));
        return futureId;
    }

    @SuppressWarnings({"unchecked"})
    public void registerPayloadHandler(Object owner) {
        var clazz = owner.getClass();
        if (owner instanceof Class) {
            clazz = (Class<?>) owner;
        }

        for (var method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(HandleFuture.class)) continue;

            var isStatic = Modifier.isStatic(method.getModifiers());
            if (!isStatic && owner instanceof Class) {
                AcademyCraft.LOGGER.warn("Cannot register non-static @HandlePayload method {} from a Class object.", method.getName());
                continue;
            }
            if (isStatic && !(owner instanceof Class)) {
                AcademyCraft.LOGGER.warn("Should register static @HandlePayload method {} using its Class object.", method.getName());
            }

            if (method.getParameterCount() != 1 || !RequestPacket.class.isAssignableFrom(method.getParameterTypes()[0])) {
                AcademyCraft.LOGGER.error("Method {} annotated with @HandlePayload must have one IRequestPayload parameter.", method.getName());
                continue;
            }
            if (!ResponsePacket.class.isAssignableFrom(method.getReturnType()) || method.getReturnType() == void.class) {
                AcademyCraft.LOGGER.error("Method {} annotated with @HandlePayload must return a type implementing IResponsePayload.", method.getName());
                continue;
            }

            var requestType = (Class<? extends RequestPacket<?, ?, ?, ?>>) method.getParameterTypes()[0];
            var responseType = (Class<? extends ResponsePacket<?, ?>>) method.getReturnType();

            var invoker = isStatic
                    ? PayloadHandlerInvokerFactory.createStaticInvoker(method, requestType, responseType)
                    : PayloadHandlerInvokerFactory.createInstanceInvoker(method, requestType, responseType, owner);

            var requestTypeId = NetworkSystem.getPacketType(requestType).getPacketId();
            requestHandlers.put(requestTypeId, invoker);
        }
    }

    @SuppressWarnings({"unchecked"})
    protected <
            REQ_L extends PacketListener,
            F_P extends FuturePacket<REQ_L, F_P>,
            RES_L extends PacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            > void handleRequest(F_P futureRequestPacket, REQ_L packetListener, Consumer<RES_P> responseSender) {
        var targetPacketTypeId = futureRequestPacket.getTargetPacketTypeId();

        if (!requestHandlers.containsKey(targetPacketTypeId)) {
            AcademyCraft.LOGGER.error("No handler for request payload ID {}", targetPacketTypeId);
            return;
        }

        var invoker = (IFutureHandlerInvoker<RES_L, RES_P, REQ_L, REQ_P>) requestHandlers.get(targetPacketTypeId);

        var packetType = NetworkSystem.<PacketType<REQ_L, REQ_P>>getPacketTypeById(targetPacketTypeId);

        var codec = packetType.codec();

        var instance = codec.decode(new FriendlyByteBuf(Unpooled.buffer()).writeByteArray(futureRequestPacket.getBytes()));
        instance.setPacketListener(packetListener);

        var responsePayload = invoker.invoke(instance);

        responseSender.accept(responsePayload);
    }

    protected <
            L extends PacketListener,
            F_P extends FuturePacket<L, F_P>,
            RES_P extends ResponsePacket<L, RES_P>
            >
    void handleResponse(F_P responsePacket, Consumer<RES_P> callbackExecutor) {
        var targetPacketTypeId = responsePacket.getTargetPacketTypeId();
        var info = pendingFutures.get(responsePacket.getFutureId());
        if (info == null) {
            AcademyCraft.LOGGER.warn("Received response for unknown/timed-out futureId: {}", responsePacket.getFutureId());
            return;
        }

        if (info.expectedResponsePacketId != -1 && info.expectedResponsePacketId != targetPacketTypeId) {
            AcademyCraft.LOGGER.error("Mismatched response payload. Expected ID {}, Got ID {}", info.expectedResponsePacketId, targetPacketTypeId);
            return;
        }

        var codec = NetworkSystem.<PacketType<L, RES_P>>getPacketTypeById(targetPacketTypeId).codec();

        try {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            var responsePayload = codec.decode(buffer.writeByteArray(responsePacket.getBytes()));
            responsePayload.setPacketListener(responsePacket.getPacketListener());
            callbackExecutor.accept(responsePayload);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Error processing response for futureId {}: {}", responsePacket.getFutureId(), e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    protected void executeCallback(int futureId, ResponsePacket<?, ?> payload) {
        var info = pendingFutures.remove(futureId);
        if (info != null) {
            try {
                ((Consumer<ResponsePacket<?, ?>>) info.callback()).accept(payload);
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Error executing callback for futureId {}: {}", futureId, e.getMessage(), e);
            }
        } else {
            AcademyCraft.LOGGER.warn("Response for futureId {} arrived, but future was already handled/timed out.", futureId);
        }
    }

    private void cleanupTimedOutFutures() {
        var now = System.currentTimeMillis();
        pendingFutures.forEach((id, info) -> {
            if (now > info.expireTime()) {
                AcademyCraft.LOGGER.warn("Future {} timed out.", id);
                if (pendingFutures.remove(id, info)) {
                    try {
                        info.callback().accept(null);
                    } catch (Exception e) {
                        AcademyCraft.LOGGER.error("Error executing timeout callback for futureId {}", id, e);
                    }
                }
            }
        });
    }
}