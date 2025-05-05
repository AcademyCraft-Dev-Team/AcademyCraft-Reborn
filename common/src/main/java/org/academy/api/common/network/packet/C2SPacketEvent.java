package org.academy.api.common.network.packet;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class C2SPacketEvent extends Event implements ICancellableEvent {
    public C2SPacket packet;

    public C2SPacketEvent(C2SPacket packet) {
        this.packet = packet;
    }
}
