package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class MouseMoveEvent extends Event implements ICancellableEvent {
    public double xpos;
    public double ypos;

    public MouseMoveEvent(double xpos, double ypos) {
        this.xpos = xpos;
        this.ypos = ypos;
    }
}