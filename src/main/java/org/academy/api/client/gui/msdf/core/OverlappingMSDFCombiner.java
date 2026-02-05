package org.academy.api.client.gui.msdf.core;

public class OverlappingMSDFCombiner implements ContourCombiner<MultiDistance> {
    private final int[] windings;
    private final MultiDistanceSelector[] edgeSelectors;
    private Point2 p = new Point2();

    public OverlappingMSDFCombiner(Shape shape) {
        var count = shape.contours.size();
        windings = new int[count];
        edgeSelectors = new MultiDistanceSelector[count];
        for (var i = 0; i < count; ++i) {
            windings[i] = shape.contours.get(i).winding();
            edgeSelectors[i] = new MultiDistanceSelector();
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
    public MultiDistance distance() {
        var contourCount = edgeSelectors.length;
        var shapeEdgeSelector = new MultiDistanceSelector();
        var innerEdgeSelector = new MultiDistanceSelector();
        var outerEdgeSelector = new MultiDistanceSelector();

        shapeEdgeSelector.reset(p);
        innerEdgeSelector.reset(p);
        outerEdgeSelector.reset(p);

        for (var i = 0; i < contourCount; ++i) {
            var edgeDistance = edgeSelectors[i].distance();
            shapeEdgeSelector.merge(edgeSelectors[i]);
            if (windings[i] > 0 && DistanceUtils.resolveDistance(edgeDistance) >= 0)
                innerEdgeSelector.merge(edgeSelectors[i]);
            if (windings[i] < 0 && DistanceUtils.resolveDistance(edgeDistance) <= 0)
                outerEdgeSelector.merge(edgeSelectors[i]);
        }

        var shapeDistance = shapeEdgeSelector.distance();
        var innerDistance = innerEdgeSelector.distance();
        var outerDistance = outerEdgeSelector.distance();
        var innerScalarDistance = DistanceUtils.resolveDistance(innerDistance);
        var outerScalarDistance = DistanceUtils.resolveDistance(outerDistance);

        var distance = new MultiDistance();
        var winding = 0;
        if (innerScalarDistance >= 0 && Math.abs(innerScalarDistance) <= Math.abs(outerScalarDistance)) {
            distance = innerDistance;
            winding = 1;
            for (var i = 0; i < contourCount; ++i) {
                if (windings[i] > 0) {
                    var contourDistance = edgeSelectors[i].distance();
                    if (Math.abs(DistanceUtils.resolveDistance(contourDistance)) < Math.abs(outerScalarDistance) &&
                            DistanceUtils.resolveDistance(contourDistance) > DistanceUtils.resolveDistance(distance))
                        distance = contourDistance;
                }
            }
        } else if (outerScalarDistance <= 0 && Math.abs(outerScalarDistance) < Math.abs(innerScalarDistance)) {
            distance = outerDistance;
            winding = -1;
            for (var i = 0; i < contourCount; ++i) {
                if (windings[i] < 0) {
                    var contourDistance = edgeSelectors[i].distance();
                    if (Math.abs(DistanceUtils.resolveDistance(contourDistance)) < Math.abs(innerScalarDistance) &&
                            DistanceUtils.resolveDistance(contourDistance) < DistanceUtils.resolveDistance(distance))
                        distance = contourDistance;
                }
            }
        } else {
            return shapeDistance;
        }

        for (var i = 0; i < contourCount; ++i) {
            if (windings[i] != winding) {
                var contourDistance = edgeSelectors[i].distance();
                if (DistanceUtils.resolveDistance(contourDistance) * DistanceUtils.resolveDistance(distance) >= 0 &&
                        Math.abs(DistanceUtils.resolveDistance(contourDistance)) < Math.abs(DistanceUtils.resolveDistance(distance)))
                    distance = contourDistance;
            }
        }

        return DistanceUtils.resolveDistance(distance) == DistanceUtils.resolveDistance(shapeDistance) ? shapeDistance : distance;
    }
}