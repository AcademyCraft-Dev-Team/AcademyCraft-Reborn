package org.academy.api.common.network.future.asm;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.future.packet.RequestPacket;
import org.academy.api.common.network.future.packet.ResponsePacket;

public abstract class InstanceFutureHandlerInvoker<
        RES_L extends PacketListener,
        RES_P extends ResponsePacket<RES_L, RES_P>,
        REQ_L extends PacketListener,
        REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
        > implements IFutureHandlerInvoker<RES_L, RES_P, REQ_L, REQ_P> {
    protected final Object instance;

    protected InstanceFutureHandlerInvoker(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null for InstancePayloadHandlerInvoker");
        }
        this.instance = instance;
    }
}