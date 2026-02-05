package org.academy.api.client.gui.msdf.core;

import java.util.ArrayList;
import java.util.List;

public class TrueDistanceSelector implements EdgeSelector {
    public static final double DISTANCE_DELTA_FACTOR = 1.001;
    protected final List<EdgeCache> caches = new ArrayList<>();
    private final SignedDistance tempDist = new SignedDistance();
    private final double[] paramCache = new double[1];
    private final double[] rootsCache = new double[3];
    protected Point2 p = new Point2();
    protected SignedDistance minDistance = new SignedDistance();
    protected int edgeIndex = 0;

    @Override
    public void reset(Point2 p) {
        var delta = DISTANCE_DELTA_FACTOR * Vector2.subtract(p, this.p).length();
        minDistance.distance += Arithmetic.nonZeroSign(minDistance.distance) * delta;
        this.p = p;
        edgeIndex = 0;
    }

    @Override
    public void addEdge(EdgeSegment prevEdge, EdgeSegment edge, EdgeSegment nextEdge) {
        if (edgeIndex >= caches.size()) {
            caches.add(new EdgeCache());
        }
        var cache = caches.get(edgeIndex++);
        var delta = DISTANCE_DELTA_FACTOR * Vector2.subtract(p, cache.point).length();
        if (cache.absDistance - delta <= Math.abs(minDistance.distance)) {
            edge.signedDistance(p, paramCache, tempDist, rootsCache);
            if (SignedDistance.lessThan(tempDist, minDistance)) {
                minDistance.distance = tempDist.distance;
                minDistance.dot = tempDist.dot;
            }
            cache.point = p;
            cache.absDistance = Math.abs(tempDist.distance);
        }
    }

    @Override
    public void merge(EdgeSelector other) {
        if (other instanceof TrueDistanceSelector o) {
            if (SignedDistance.lessThan(o.minDistance, minDistance)) {
                minDistance.distance = o.minDistance.distance;
                minDistance.dot = o.minDistance.dot;
            }
        }
    }

    public double distance() {
        return minDistance.distance;
    }

    public static class EdgeCache {
        public Point2 point = new Point2();
        public double absDistance;
    }
}