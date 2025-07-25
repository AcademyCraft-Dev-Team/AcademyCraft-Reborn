package org.academy.api.client.input;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class MouseMoveEvent extends Event implements ICancellableEvent {
    public double xpos;
    public double ypos;

    public MouseMoveEvent(double newXpos, double newYpos) {
        xpos = newXpos;
        ypos = newYpos;
    }
}