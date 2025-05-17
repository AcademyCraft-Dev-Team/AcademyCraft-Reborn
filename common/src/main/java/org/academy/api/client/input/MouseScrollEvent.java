package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class MouseScrollEvent extends Event implements ICancellableEvent {
    public long windowPointer;
    public double xOffset;
    public double yOffset;

    public MouseScrollEvent(long windowPointer, double xOffset, double yOffset) {
        this.windowPointer = windowPointer;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }
}