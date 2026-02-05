package org.academy.api.client.gui.msdf.core;

public class SimpleMSDFCombiner implements ContourCombiner<MultiDistance> {
    private final MultiDistanceSelector shapeEdgeSelector = new MultiDistanceSelector();

    @Override
    public void reset(Point2 p) {
        shapeEdgeSelector.reset(p);
    }

    @Override
    public EdgeSelector edgeSelector(int i) {
        return shapeEdgeSelector;
    }

    @Override
    public MultiDistance distance() {
        return shapeEdgeSelector.distance();
    }
}