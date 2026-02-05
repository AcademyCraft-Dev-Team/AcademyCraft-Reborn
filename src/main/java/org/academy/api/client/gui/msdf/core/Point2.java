package org.academy.api.client.gui.msdf.core;

public class Point2 extends Vector2 {
    public Point2() {
    }

    public Point2(double val) {
        super(val);
    }

    public Point2(Vector2 vector) {
        super(vector.x, vector.y);
    }

    public Point2(double x, double y) {
        super(x, y);
    }
}
