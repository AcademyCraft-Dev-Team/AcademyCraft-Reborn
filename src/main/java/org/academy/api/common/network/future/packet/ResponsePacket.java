package org.academy.api.common.network.future.packet;

import net.minecraft.network.PacketListener;
import org.academy.api.common.network.packet.Packet;

public abstract class ResponsePacket<T extends PacketListener, P extends Packet<T, P>> extends Packet<T, P> {
}