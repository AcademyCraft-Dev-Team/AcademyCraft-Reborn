package org.academy.api.common.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.academy.AcademyCraft;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.future.annotation.HandleFuture;
import org.academy.api.common.network.future.asm.IFutureHandlerInvoker;
import org.academy.api.common.network.future.asm.PayloadHandlerInvokerFactory;
import org.academy.api.common.network.future.packet.FuturePacket;
import org.academy.api.common.network.future.packet.FutureRequestPacket;
import org.academy.api.common.network.future.packet.RequestPacket;
import org.academy.api.common.network.future.packet.ResponsePacket;
import org.academy.api.common.network.packet.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.academy.AcademyCraft.LOGGER_PREFIX;

/**
 * 发送端发送一个 FutureRequestPacket 后由各端的 FutureManager 的 IPacketListener 处理喵
 * <br>
 * 发送端 S, 目标端 R, 目标端 IPacketListener H
 * <br>
 * ****Send
 * <br>
 * 1. S ------> R
 * <br>
 * *****************Send
 * <br>
 * ****Consume
 * <br>
 * 2. R -----------> H ------> S
 */
public abstract class AbstractFutureManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_PREFIX + "Future");
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
    public final void registerPayloadHandler(Object owner) {
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

    /**
     * 处理 FutureRequestPacket 喵
     * <br>
     * Request 发送过来后, 由带有 HandleFuture 注解的方法处理并返回 ResponsePacket 喵, 随后后将 Response 发送回去喵
     *
     * @param futureRequestPacket 各端 FutureManager 传来的实例喵
     * @param packetListener 当前端的 PacketListener 喵
     * @param responseSender 用于当前端发送 ResponsePacket 喵
     * @param <REQ_L> 当前端的 PacketListener 的泛型喵
     * @param <RES_L> 目标端的 PacketListener 喵, 只是泛型占位而已喵, 没有使用喵
     * @param <RES_P> RequestPacket 期望的 ResponsePacket 的泛型喵, responsePacket 的类型喵
     * @param <REQ_P> RequestPacket 的泛型喵, instance 的类型喵
     */
    @SuppressWarnings({"unchecked"})
    protected <
            REQ_L extends PacketListener,
            RES_L extends PacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            > void handleRequest(FutureRequestPacket<REQ_L> futureRequestPacket, REQ_L packetListener, Consumer<RES_P> responseSender) {
        var targetPacketTypeId = futureRequestPacket.getTargetPacketTypeId();

        var requestHandler = requestHandlers.get(targetPacketTypeId);
        if (requestHandler == null) {
            AcademyCraft.LOGGER.error("No handler for request payload ID {}", targetPacketTypeId);
            return;
        }

        var invoker = (IFutureHandlerInvoker<RES_L, RES_P, REQ_L, REQ_P>) requestHandlers.get(targetPacketTypeId);

        var packetType = NetworkSystem.<PacketType<REQ_L, REQ_P>>getPacketTypeById(targetPacketTypeId);

        var codec = packetType.codec();
        var bytes = futureRequestPacket.getBytes();

        // 有 debugInfo 就够了喵, 所以 info 不需要改喵
        if (NetworkSystem.debugInfo) AcademyCraft.LOGGER.info(Arrays.toString(bytes));

        var instance = codec.decode(Unpooled.buffer().writeBytes(bytes));
        instance.setPacketListener(packetListener);

        var responsePacket = invoker.invoke(instance);

        responseSender.accept(responsePacket);
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
            var bytes = responsePacket.getBytes();
            AcademyCraft.LOGGER.info(Arrays.toString(bytes));
            var responsePayload = codec.decode(buffer.writeBytes(bytes));
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