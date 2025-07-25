package org.academy.api.common.network.packet;

import net.neoforged.bus.api.Event;

public final class C2SPacketEvent extends Event {
    public C2SPacket packet;

    public C2SPacketEvent(C2SPacket newPacket) {
        packet = newPacket;
    }
}