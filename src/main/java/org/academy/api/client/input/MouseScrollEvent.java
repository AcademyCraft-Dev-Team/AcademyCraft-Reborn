package org.academy.api.client.input;


public class MouseScrollEvent extends InputControlEvent {
    public double xOffset;
    public double yOffset;

    public MouseScrollEvent(double xOffset, double yOffset) {
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }
}
