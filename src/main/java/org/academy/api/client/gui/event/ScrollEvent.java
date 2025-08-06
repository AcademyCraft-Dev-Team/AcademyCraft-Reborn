package org.academy.api.client.gui.event;

public class ScrollEvent extends InputEvent {
    private final double x;
    private final double y;
    private final double delta;

    public ScrollEvent(double x, double y, double delta) {
        super(EventType.MOUSE_SCROLLED);
        this.x = x;
        this.y = y;
        this.delta = delta;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getDelta() {
        return delta;
    }
}