package org.academy.api.client.gui.msdf.core;

public class SimpleMTSDFCombiner implements ContourCombiner<MultiAndTrueDistance> {
    private final MultiAndTrueDistanceSelector shapeEdgeSelector = new MultiAndTrueDistanceSelector();

    @Override
    public void reset(Point2 p) {
        shapeEdgeSelector.reset(p);
    }

    @Override
    public EdgeSelector edgeSelector(int i) {
        return shapeEdgeSelector;
    }

    @Override
    public MultiAndTrueDistance distance() {
        return shapeEdgeSelector.distance();
    }
}