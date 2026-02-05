package org.academy.api.client.gui.msdf.core;

public class EdgeHolder {
    private EdgeSegment edgeSegment;

    public EdgeHolder() {
        edgeSegment = null;
    }

    public EdgeHolder(EdgeSegment segment) {
        edgeSegment = segment;
    }

    public EdgeHolder(Point2 p0, Point2 p1, int edgeColor) {
        edgeSegment = EdgeSegment.create(p0, p1, edgeColor);
    }

    public EdgeHolder(Point2 p0, Point2 p1, Point2 p2, int edgeColor) {
        edgeSegment = EdgeSegment.create(p0, p1, p2, edgeColor);
    }

    public EdgeHolder(Point2 p0, Point2 p1, Point2 p2, Point2 p3, int edgeColor) {
        edgeSegment = EdgeSegment.create(p0, p1, p2, p3, edgeColor);
    }

    public static void swap(EdgeHolder a, EdgeHolder b) {
        var tmp = a.edgeSegment;
        a.edgeSegment = b.edgeSegment;
        b.edgeSegment = tmp;
    }

    public void assign(EdgeHolder orig) {
        if (this != orig) edgeSegment = orig.edgeSegment != null ? orig.edgeSegment.clone() : null;
    }

    public EdgeSegment get() {
        return edgeSegment;
    }

    public boolean isNull() {
        return edgeSegment == null;
    }
}