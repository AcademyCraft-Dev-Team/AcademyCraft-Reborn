package org.academy.api.client.input;


public class MouseMoveEvent extends InputControlEvent {
    public double xpos;
    public double ypos;

    public MouseMoveEvent(double newXpos, double newYpos) {
        xpos = newXpos;
        ypos = newYpos;
    }
}
