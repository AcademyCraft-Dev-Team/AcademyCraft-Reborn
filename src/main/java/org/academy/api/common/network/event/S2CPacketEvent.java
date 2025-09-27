package org.academy.api.common.network.event;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.api.common.network.packet.S2CPacket;

public class S2CPacketEvent extends Event implements ICancellableEvent {
    private final S2CPacket packet;

    public S2CPacketEvent(S2CPacket packet) {
        this.packet = packet;
    }

    public S2CPacket getPacket() {
        return packet;
    }
}