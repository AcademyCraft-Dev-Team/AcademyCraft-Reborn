package org.academy.api.client.gui.msdf.core;

public class OverlappingSDFCombiner implements ContourCombiner<Double> {
    private final int[] windings;
    private final TrueDistanceSelector[] edgeSelectors;
    private Point2 p = new Point2();

    public OverlappingSDFCombiner(Shape shape) {
        var count = shape.contours.size();
        windings = new int[count];
        edgeSelectors = new TrueDistanceSelector[count];
        for (var i = 0; i < count; ++i) {
            windings[i] = shape.contours.get(i).winding();
            edgeSelectors[i] = new TrueDistanceSelector();
        }
    }

    @Override
    public void reset(Point2 p) {
        this.p = p;
        for (var selector : edgeSelectors) {
            selector.reset(p);
        }
    }

    @Override
    public EdgeSelector edgeSelector(int i) {
        return edgeSelectors[i];
    }

    @Override
    public Double distance() {
        var contourCount = edgeSelectors.length;
        var shapeEdgeSelector = new TrueDistanceSelector();
        var innerEdgeSelector = new TrueDistanceSelector();
        var outerEdgeSelector = new TrueDistanceSelector();

        shapeEdgeSelector.reset(p);
        innerEdgeSelector.reset(p);
        outerEdgeSelector.reset(p);

        for (var i = 0; i < contourCount; ++i) {
            var edgeDistance = edgeSelectors[i].distance();
            shapeEdgeSelector.merge(edgeSelectors[i]);
            if (windings[i] > 0 && edgeDistance >= 0)
                innerEdgeSelector.merge(edgeSelectors[i]);
            if (windings[i] < 0 && edgeDistance <= 0)
                outerEdgeSelector.merge(edgeSelectors[i]);
        }

        var shapeDistance = shapeEdgeSelector.distance();
        var innerDistance = innerEdgeSelector.distance();
        var outerDistance = outerEdgeSelector.distance();

        var distance = -Double.MAX_VALUE;
        var winding = 0;
        if (innerDistance >= 0 && Math.abs(innerDistance) <= Math.abs(outerDistance)) {
            distance = innerDistance;
            winding = 1;
            for (var i = 0; i < contourCount; ++i) {
                if (windings[i] > 0) {
                    var contourDistance = edgeSelectors[i].distance();
                    if (Math.abs(contourDistance) < Math.abs(outerDistance) && contourDistance > distance)
                        distance = contourDistance;
                }
            }
        } else if (outerDistance <= 0 && Math.abs(outerDistance) < Math.abs(innerDistance)) {
            distance = outerDistance;
            winding = -1;
            for (var i = 0; i < contourCount; ++i) {
                if (windings[i] < 0) {
                    var contourDistance = edgeSelectors[i].distance();
                    if (Math.abs(contourDistance) < Math.abs(innerDistance) && contourDistance < distance)
                        distance = contourDistance;
                }
            }
        } else {
            return shapeDistance;
        }

        for (var i = 0; i < contourCount; ++i) {
            if (windings[i] != winding) {
                var contourDistance = edgeSelectors[i].distance();
                if (contourDistance * distance >= 0 && Math.abs(contourDistance) < Math.abs(distance))
                    distance = contourDistance;
            }
        }

        return distance == shapeDistance ? shapeDistance : distance;
    }
}