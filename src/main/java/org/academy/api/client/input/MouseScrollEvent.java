package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class MouseScrollEvent extends Event implements ICancellableEvent {
    public double xOffset;
    public double yOffset;

    public MouseScrollEvent(double xOffset, double yOffset) {
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }
}