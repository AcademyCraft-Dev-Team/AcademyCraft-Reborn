package org.academy.api.client.gui.msdf.core;

public class ShapeDistanceFinder<D> {
    private final Shape shape;
    private final ContourCombiner<D> combiner;

    public ShapeDistanceFinder(Shape shape, ContourCombiner<D> combiner) {
        this.shape = shape;
        this.combiner = combiner;
    }

    public D distance(Point2 origin) {
        combiner.reset(origin);
        for (var i = 0; i < shape.contours.size(); i++) {
            var contour = shape.contours.get(i);
            if (contour.edges.isEmpty()) continue;
            var selector = combiner.edgeSelector(i);
            var edgeCount = contour.edges.size();
            var prevEdge = edgeCount >= 2 ? contour.edges.get(edgeCount - 2).get() : contour.edges.getFirst().get();
            var curEdge = contour.edges.get(edgeCount - 1).get();
            for (var edgeHolder : contour.edges) {
                var nextEdge = edgeHolder.get();
                selector.addEdge(prevEdge, curEdge, nextEdge);
                prevEdge = curEdge;
                curEdge = nextEdge;
            }
        }
        return combiner.distance();
    }
}