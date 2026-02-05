package org.academy.api.client.gui.msdf.core;

import java.util.ArrayList;
import java.util.List;

public abstract class PerpendicularDistanceSelectorBase implements EdgeSelector {
    public static final double DISTANCE_DELTA_FACTOR = 1.001;
    protected final List<EdgeCache> caches = new ArrayList<>();
    private final SignedDistance tempDist = new SignedDistance();
    protected SignedDistance minTrueDistance = new SignedDistance();
    protected double minNegativePerpendicularDistance;
    protected double minPositivePerpendicularDistance;
    protected EdgeSegment nearEdge;
    protected double nearEdgeParam;
    protected int edgeIndex = 0;

    public PerpendicularDistanceSelectorBase() {
        minNegativePerpendicularDistance = -Math.abs(minTrueDistance.distance);
        minPositivePerpendicularDistance = Math.abs(minTrueDistance.distance);
    }

    public static boolean getPerpendicularDistance(double[] distance, Vector2 ep, Vector2 edgeDir) {
        var ts = Vector2.dotProduct(ep, edgeDir);
        if (ts > 0) {
            var perpendicularDistance = Vector2.crossProduct(ep, edgeDir);
            if (Math.abs(perpendicularDistance) < Math.abs(distance[0])) {
                distance[0] = perpendicularDistance;
                return true;
            }
        }
        return false;
    }

    public void reset(double delta) {
        minTrueDistance.distance += Arithmetic.nonZeroSign(minTrueDistance.distance) * delta;
        minNegativePerpendicularDistance = -Math.abs(minTrueDistance.distance);
        minPositivePerpendicularDistance = Math.abs(minTrueDistance.distance);
        nearEdge = null;
        nearEdgeParam = 0;
        edgeIndex = 0;
    }

    protected EdgeCache getNextCache() {
        if (edgeIndex >= caches.size()) {
            caches.add(new EdgeCache());
        }
        return caches.get(edgeIndex++);
    }

    public boolean isEdgeRelevant(EdgeCache cache, EdgeSegment edge, Point2 p) {
        var delta = DISTANCE_DELTA_FACTOR * Vector2.subtract(p, cache.point).length();
        return (
                cache.absDistance - delta <= Math.abs(minTrueDistance.distance) ||
                        Math.abs(cache.aDomainDistance) < delta ||
                        Math.abs(cache.bDomainDistance) < delta ||
                        (cache.aDomainDistance > 0 && (cache.aPerpendicularDistance < 0 ?
                                cache.aPerpendicularDistance + delta >= minNegativePerpendicularDistance :
                                cache.aPerpendicularDistance - delta <= minPositivePerpendicularDistance
                        )) ||
                        (cache.bDomainDistance > 0 && (cache.bPerpendicularDistance < 0 ?
                                cache.bPerpendicularDistance + delta >= minNegativePerpendicularDistance :
                                cache.bPerpendicularDistance - delta <= minPositivePerpendicularDistance
                        ))
        );
    }

    public void addEdgeTrueDistance(EdgeSegment edge, SignedDistance distance, double param) {
        if (SignedDistance.lessThan(distance, minTrueDistance)) {
            minTrueDistance.distance = distance.distance;
            minTrueDistance.dot = distance.dot;
            nearEdge = edge;
            nearEdgeParam = param;
        }
    }

    public void addEdgePerpendicularDistance(double distance) {
        if (distance <= 0 && distance > minNegativePerpendicularDistance)
            minNegativePerpendicularDistance = distance;
        if (distance >= 0 && distance < minPositivePerpendicularDistance)
            minPositivePerpendicularDistance = distance;
    }

    @Override
    public void merge(EdgeSelector other) {
        if (other instanceof PerpendicularDistanceSelectorBase o) {
            if (SignedDistance.lessThan(o.minTrueDistance, minTrueDistance)) {
                minTrueDistance.distance = o.minTrueDistance.distance;
                minTrueDistance.dot = o.minTrueDistance.dot;
                nearEdge = o.nearEdge;
                nearEdgeParam = o.nearEdgeParam;
            }
            if (o.minNegativePerpendicularDistance > minNegativePerpendicularDistance)
                minNegativePerpendicularDistance = o.minNegativePerpendicularDistance;
            if (o.minPositivePerpendicularDistance < minPositivePerpendicularDistance)
                minPositivePerpendicularDistance = o.minPositivePerpendicularDistance;
        }
    }

    public double computeDistance(Point2 p) {
        var minDistance = minTrueDistance.distance < 0 ? minNegativePerpendicularDistance : minPositivePerpendicularDistance;
        if (nearEdge != null) {
            tempDist.distance = minTrueDistance.distance;
            tempDist.dot = minTrueDistance.dot;
            nearEdge.distanceToPerpendicularDistance(tempDist, p, nearEdgeParam);
            if (Math.abs(tempDist.distance) < Math.abs(minDistance))
                minDistance = tempDist.distance;
        }
        return minDistance;
    }

    public SignedDistance trueDistance() {
        return minTrueDistance;
    }

    public static class EdgeCache {
        public Point2 point = new Point2();
        public double absDistance;
        public double aDomainDistance, bDomainDistance;
        public double aPerpendicularDistance, bPerpendicularDistance;
    }
}