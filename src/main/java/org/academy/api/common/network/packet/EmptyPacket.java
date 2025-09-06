package org.academy.api.common.network.packet;

import net.minecraft.network.PacketListener;

public abstract class EmptyPacket<T extends PacketListener, P extends EmptyPacket<T, P>> extends Packet<T, P> {
}