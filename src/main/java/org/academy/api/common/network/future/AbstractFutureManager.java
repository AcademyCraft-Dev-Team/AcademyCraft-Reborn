package org.academy.api.common.network.future;

import net.minecraft.network.PacketListener;
import org.academy.AcademyCraft;
import org.academy.api.common.network.future.asm.IPayloadHandlerInvoker;
import org.academy.api.common.network.future.asm.PayloadHandlerInvokerFactory;
import org.academy.api.common.network.packet.FuturePacket;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class AbstractFutureManager {
    protected final Map<Integer, PendingFutureInfo> pendingFutures = new ConcurrentHashMap<>();
    protected final Map<Integer, IPayloadHandlerInvoker> requestHandlers = new ConcurrentHashMap<>();
    private final AtomicInteger nextFutureId = new AtomicInteger(0);
    protected static final long DEFAULT_TIMEOUT_MS = 60000;

    protected record PendingFutureInfo(Consumer<?> callback, int expectedResponsePayloadId, long expireTime) {
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

    protected <T_RESP extends Payload<?>> int createPendingFuture(PayloadType<?, T_RESP> responsePayloadType, Consumer<T_RESP> callback, long timeoutMillis) {
        int futureId = generateFutureId();
        int expectedResponsePayloadId = responsePayloadType.getPayloadId();
        if (expectedResponsePayloadId == -1) {
            AcademyCraft.LOGGER.error("FutureManager: Response payload type {} is not registered.", responsePayloadType.getPayloadClass().getName());
            return -1;
        }
        long expireTime = System.currentTimeMillis() + timeoutMillis;
        pendingFutures.put(futureId, new PendingFutureInfo(callback, expectedResponsePayloadId, expireTime));
        return futureId;
    }

    @SuppressWarnings({"unchecked", "DuplicatedCode"})
    public void registerPayloadHandler(Object owner) {
        var clazz = owner.getClass();
        if (owner instanceof Class) {
            clazz = (Class<?>) owner;
        }

        for (var method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(HandlePayload.class)) continue;

            var isStatic = Modifier.isStatic(method.getModifiers());
            if (!isStatic && owner instanceof Class) {
                AcademyCraft.LOGGER.warn("Cannot register non-static @HandlePayload method {} from a Class object.", method.getName());
                continue;
            }
            if (isStatic && !(owner instanceof Class)) {
                AcademyCraft.LOGGER.warn("Should register static @HandlePayload method {} using its Class object.", method.getName());
            }

            if (method.getParameterCount() != 1 || !RequestPayload.class.isAssignableFrom(method.getParameterTypes()[0])) {
                AcademyCraft.LOGGER.error("Method {} annotated with @HandlePayload must have one IRequestPayload parameter.", method.getName());
                continue;
            }
            if (!ResponsePayload.class.isAssignableFrom(method.getReturnType()) || method.getReturnType() == void.class) {
                AcademyCraft.LOGGER.error("Method {} annotated with @HandlePayload must return a type implementing IResponsePayload.", method.getName());
                continue;
            }

            var requestType = (Class<? extends RequestPayload<?, ?>>) method.getParameterTypes()[0];
            var responseType = (Class<? extends ResponsePayload<?>>) method.getReturnType();

            var invoker = isStatic
                    ? PayloadHandlerInvokerFactory.createStaticInvoker(method, requestType, responseType)
                    : PayloadHandlerInvokerFactory.createInstanceInvoker(method, requestType, responseType, owner);

            var requestTypeId = FutureManager.getPayloadType(requestType).getPayloadId();
            requestHandlers.put(requestTypeId, invoker);
        }
    }

    protected <L extends PacketListener> void handleRequest(FuturePacket<L> requestPacket, L packetListener, Consumer<Payload<?>> responseSender) {
        var payloadTypeId = requestPacket.payloadTypeId;
        var invoker = requestHandlers.get(payloadTypeId);
        if (invoker == null) {
            AcademyCraft.LOGGER.error("No handler for request payload ID {}", payloadTypeId);
            return;
        }

        var payloadType = FutureManager.<PacketListener, RequestPayload<PacketListener, ?>>getPayloadTypeById(payloadTypeId);

        var factory = payloadType.getFactory();
        if (factory == null) {
            AcademyCraft.LOGGER.error("No factory for request payload ID {}", payloadTypeId);
            return;
        }

        var requestPayload = factory.apply(packetListener);
        requestPayload.read(requestPacket.payloadData);

        var responsePayload = invoker.invoke(requestPayload);
        if (responsePayload != null) {
            responseSender.accept(responsePayload);
        }
    }

    protected <L extends PacketListener> void handleResponse(FuturePacket<L> responsePacket, Consumer<Payload<?>> callbackExecutor) {
        var payloadTypeId = responsePacket.payloadTypeId;
        var info = pendingFutures.get(responsePacket.futureId);
        if (info == null) {
            AcademyCraft.LOGGER.warn("Received response for unknown/timed-out futureId: {}", responsePacket.futureId);
            return;
        }

        if (info.expectedResponsePayloadId != -1 && info.expectedResponsePayloadId != payloadTypeId) {
            AcademyCraft.LOGGER.error("Mismatched response payload. Expected ID {}, Got ID {}", info.expectedResponsePayloadId, payloadTypeId);
            return;
        }

        var factory = FutureManager.getPayloadTypeById(payloadTypeId).getFactory();

        try {
            var responsePayload = factory.apply(null);
            responsePayload.read(responsePacket.payloadData);
            callbackExecutor.accept(responsePayload);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Error processing response for futureId {}: {}", responsePacket.futureId, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    protected void executeCallback(int futureId, Payload<?> payload) {
        PendingFutureInfo info = pendingFutures.remove(futureId);
        if (info != null) {
            if (info.callback() != null) {
                try {
                    ((Consumer<Payload<?>>) info.callback()).accept(payload);
                } catch (Exception e) {
                    AcademyCraft.LOGGER.error("Error executing callback for futureId {}: {}", futureId, e.getMessage(), e);
                }
            }
        } else {
            AcademyCraft.LOGGER.warn("Response for futureId {} arrived, but future was already handled/timed out.", futureId);
        }
    }

    private void cleanupTimedOutFutures() {
        long now = System.currentTimeMillis();
        pendingFutures.forEach((id, info) -> {
            if (now > info.expireTime()) {
                AcademyCraft.LOGGER.warn("Future {} timed out.", id);
                if (pendingFutures.remove(id, info) && info.callback() != null) {
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