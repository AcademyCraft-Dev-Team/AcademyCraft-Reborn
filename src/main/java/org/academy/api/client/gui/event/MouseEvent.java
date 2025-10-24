package org.academy.api.client.gui.framework.event;

public final class MouseEvent extends InputEvent {
    private final double x;
    private final double y;
    private final int button;
    private final double dragX;
    private final double dragY;

    private MouseEvent(EventType type, double x, double y, int button, double dragX, double dragY) {
        super(type);
        this.x = x;
        this.y = y;
        this.button = button;
        this.dragX = dragX;
        this.dragY = dragY;
    }

    public static MouseEvent createPressEvent(double x, double y, int button) {
        return new MouseEvent(EventType.MOUSE_PRESSED, x, y, button, 0, 0);
    }

    public static MouseEvent createReleaseEvent(double x, double y, int button) {
        return new MouseEvent(EventType.MOUSE_RELEASED, x, y, button, 0, 0);
    }

    public static MouseEvent createMoveEvent(double x, double y) {
        return new MouseEvent(EventType.MOUSE_MOVED, x, y, -1, 0, 0);
    }

    public static MouseEvent createDragEvent(double x, double y, int button, double dragX, double dragY) {
        return new MouseEvent(EventType.MOUSE_DRAGGED, x, y, button, dragX, dragY);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getButton() {
        return button;
    }

    public double getDragX() {
        return dragX;
    }

    public double getDragY() {
        return dragY;
    }
}