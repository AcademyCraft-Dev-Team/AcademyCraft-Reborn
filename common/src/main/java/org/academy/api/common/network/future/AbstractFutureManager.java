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
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractFutureManager {
    protected final Map<Integer, PendingFutureInfo> pendingFutures = new ConcurrentHashMap<>();
    protected final Map<Integer, IPayloadHandlerInvoker> requestHandlers = new ConcurrentHashMap<>();
    protected final FutureManager futureManager;
    private final AtomicInteger nextFutureId = new AtomicInteger(0);
    protected static final long DEFAULT_TIMEOUT_MS = 60000;

    protected record PendingFutureInfo(Consumer<?> callback, int expectedResponsePayloadId, long expireTime) {
    }

    protected AbstractFutureManager(FutureManager newFutureManager) {
        futureManager = newFutureManager;
        AcademyCraft.executorService.scheduleAtFixedRate(this::cleanupTimedOutFutures, 1, 1, TimeUnit.SECONDS);
    }

    public void clear() {
        pendingFutures.clear();
        requestHandlers.clear();
    }

    protected int generateFutureId() {
        return nextFutureId.getAndIncrement();
    }

    protected <T_RESP extends IPayload> int createPendingFuture(Class<T_RESP> responseClass, Consumer<T_RESP> callback, long timeoutMillis) {
        var futureId = generateFutureId();
        var expectedResponsePayloadId = futureManager.getPayloadId(responseClass);
        if (expectedResponsePayloadId == -1 && responseClass != null) {
            AcademyCraft.LOGGER.error("FutureManager: Response payload type {} is not registered.", responseClass.getName());
            return -1;
        }
        var expireTime = System.currentTimeMillis() + timeoutMillis;
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

            if (method.getParameterCount() != 1 || !IRequestPayload.class.isAssignableFrom(method.getParameterTypes()[0])) {
                AcademyCraft.LOGGER.error("Method {} annotated with @HandlePayload must have one IRequestPayload parameter.", method.getName());
                continue;
            }
            if (!IResponsePayload.class.isAssignableFrom(method.getReturnType()) || method.getReturnType() == void.class) {
                AcademyCraft.LOGGER.error("Method {} annotated with @HandlePayload must return a type implementing IResponsePayload.", method.getName());
                continue;
            }

            var requestType = (Class<? extends IRequestPayload<?, ?>>) method.getParameterTypes()[0];
            var responseType = (Class<? extends IResponsePayload>) method.getReturnType();

            var invoker = isStatic
                    ? PayloadHandlerInvokerFactory.createStaticInvoker(method, requestType, responseType)
                    : PayloadHandlerInvokerFactory.createInstanceInvoker(method, requestType, responseType, owner);

            var requestTypeId = futureManager.getPayloadId(requestType);
            requestHandlers.put(requestTypeId, invoker);
        }
    }

    @SuppressWarnings("unchecked")
    protected <L extends PacketListener> void handleRequest(FuturePacket<L> requestPacket, Supplier<L> listenerSupplier, Consumer<IPayload> responseSender) {
        var invoker = requestHandlers.get(requestPacket.payloadTypeId);
        if (invoker == null) {
            AcademyCraft.LOGGER.error("No handler for request payload ID {}", requestPacket.payloadTypeId);
            return;
        }

        var rawFactory = futureManager.getPayloadFactory(requestPacket.payloadTypeId);
        if (rawFactory == null) {
            AcademyCraft.LOGGER.error("No factory for request payload ID {}", requestPacket.payloadTypeId);
            return;
        }
        var factory = (Function<L, ? extends IRequestPayload<L, ?>>) rawFactory;

        var requestPayload = factory.apply(listenerSupplier.get());
        requestPayload.read(requestPacket.payloadData);
        requestPayload.packetListenerSupplier = listenerSupplier;

        var responsePayload = invoker.invoke(requestPayload);
        if (responsePayload != null) {
            responseSender.accept(responsePayload);
        }
    }

    @SuppressWarnings("unchecked")
    protected <L extends PacketListener> void handleResponse(FuturePacket<L> responsePacket, Consumer<IPayload> callbackExecutor) {
        var info = pendingFutures.get(responsePacket.futureId);
        if (info == null) {
            AcademyCraft.LOGGER.warn("Received response for unknown/timed-out futureId: {}", responsePacket.futureId);
            return;
        }

        if (info.expectedResponsePayloadId() != -1 && info.expectedResponsePayloadId() != responsePacket.payloadTypeId) {
            AcademyCraft.LOGGER.error("Mismatched response payload. Expected ID {}, Got ID {}", info.expectedResponsePayloadId(), responsePacket.payloadTypeId);
            return;
        }

        var rawFactory = futureManager.getPayloadFactory(responsePacket.payloadTypeId);
        if (rawFactory == null) {
            AcademyCraft.LOGGER.error("No factory for response payload ID {}", responsePacket.payloadTypeId);
            return;
        }
        var factory = (Function<L, ? extends IPayload>) rawFactory;

        try {
            var responsePayload = factory.apply(null);
            responsePayload.read(responsePacket.payloadData);
            callbackExecutor.accept(responsePayload);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Error processing response for futureId {}: {}", responsePacket.futureId, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    protected void executeCallback(int futureId, IPayload payload) {
        var info = pendingFutures.remove(futureId);
        if (info != null) {
            if (info.callback() != null) {
                try {
                    ((Consumer<IPayload>) info.callback()).accept(payload);
                } catch (Exception e) {
                    AcademyCraft.LOGGER.error("Error executing callback for futureId {}: {}", futureId, e.getMessage(), e);
                }
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