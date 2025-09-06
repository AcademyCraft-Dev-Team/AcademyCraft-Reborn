package org.academy.api.common.network.future.packet;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.packet.Packet;

public abstract class FuturePacket<T extends PacketListener, P extends FuturePacket<T, P>> extends Packet<T, P> {
    private final int futureId;
    private final int targetPacketTypeId;
    private final byte[] bytes;

    /**
     * @param futureId 此次 future 的 ID
     * @param bytes 目标所需的数据
     */
    protected FuturePacket(int futureId, int targetPacketTypeId, byte[] bytes) {
        this.futureId = futureId;
        this.targetPacketTypeId = targetPacketTypeId;
        this.bytes = bytes;
    }

    public int getFutureId() {
        return futureId;
    }

    public int getTargetPacketTypeId() {
        return targetPacketTypeId;
    }

    public byte[] getBytes() {
        return bytes;
    }
}