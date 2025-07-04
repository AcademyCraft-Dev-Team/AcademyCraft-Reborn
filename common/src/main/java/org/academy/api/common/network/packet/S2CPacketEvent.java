package org.academy.api.common.network.packet;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class S2CPacketEvent extends Event implements ICancellableEvent {
    public S2CPacket packet;

    public S2CPacketEvent(S2CPacket newPacket) {
        packet = newPacket;
    }
}