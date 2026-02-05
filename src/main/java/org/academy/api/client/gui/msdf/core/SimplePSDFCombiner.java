package org.academy.api.client.gui.msdf.core;

public class SimplePSDFCombiner implements ContourCombiner<Double> {
    private final PerpendicularDistanceSelector shapeEdgeSelector = new PerpendicularDistanceSelector();

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