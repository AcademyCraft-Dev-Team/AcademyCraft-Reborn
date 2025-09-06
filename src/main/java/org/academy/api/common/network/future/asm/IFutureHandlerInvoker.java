package org.academy.api.common.network.future.asm;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.future.packet.RequestPacket;
import org.academy.api.common.network.future.packet.ResponsePacket;

public interface IFutureHandlerInvoker<
        RES_L extends PacketListener,
        RES_P extends ResponsePacket<RES_L, RES_P>,
        REQ_L extends PacketListener,
        REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
        > {
    RES_P invoke(REQ_P requestPacket);
}