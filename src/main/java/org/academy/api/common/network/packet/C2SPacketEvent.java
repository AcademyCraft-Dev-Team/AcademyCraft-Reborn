package org.academy.api.common.network.packet;

import net.neoforged.bus.api.Event;

public final class C2SPacketEvent extends Event {
    private final C2SPacket packet;

    public C2SPacketEvent(C2SPacket packet) {
        this.packet = packet;
    }

    public C2SPacket getPacket() {
        return packet;
    }
}