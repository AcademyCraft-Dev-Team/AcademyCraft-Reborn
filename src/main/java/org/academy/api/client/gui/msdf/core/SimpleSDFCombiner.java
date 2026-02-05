package org.academy.api.client.gui.msdf.core;

public class SimpleSDFCombiner implements ContourCombiner<Double> {
    private final TrueDistanceSelector shapeEdgeSelector = new TrueDistanceSelector();

    @Override
    public void reset(Point2 p) {
        shapeEdgeSelector.reset(p);
    }

    @Override
    public EdgeSelector edgeSelector(int i) {
        return shapeEdgeSelector;
    }

    @Override
    public Double distance() {
        return shapeEdgeSelector.distance();
    }
}