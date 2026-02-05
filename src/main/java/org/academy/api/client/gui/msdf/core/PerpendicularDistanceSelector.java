package org.academy.api.client.gui.msdf.core;

public class PerpendicularDistanceSelector extends PerpendicularDistanceSelectorBase {
    private final SignedDistance tempDist = new SignedDistance();
    private final double[] paramCache = new double[1];
    private final double[] rootsCache = new double[3];
    private Point2 p = new Point2();

    @Override
    public void reset(Point2 p) {
        var delta = DISTANCE_DELTA_FACTOR * Vector2.subtract(p, this.p).length();
        reset(delta);
        this.p = p;
    }

    @Override
    public void addEdge(EdgeSegment prevEdge, EdgeSegment edge, EdgeSegment nextEdge) {
        var cache = getNextCache();
        if (isEdgeRelevant(cache, edge, p)) {
            edge.signedDistance(p, paramCache, tempDist, rootsCache);
            var param = paramCache[0];

            addEdgeTrueDistance(edge, tempDist, param);
            cache.point = p;
            cache.absDistance = Math.abs(tempDist.distance);

            var ap = Vector2.subtract(p, edge.point(0));
            var bp = Vector2.subtract(p, edge.point(1));
            var aDir = edge.direction(0).normalize(true);
            var bDir = edge.direction(1).normalize(true);
            var prevDir = prevEdge.direction(1).normalize(true);
            var nextDir = nextEdge.direction(0).normalize(true);

            var add = Vector2.dotProduct(ap, Vector2.add(prevDir, aDir).normalize(true));
            var bdd = -Vector2.dotProduct(bp, Vector2.add(bDir, nextDir).normalize(true));

            if (add > 0) {
                var pd = new double[]{tempDist.distance};
                if (getPerpendicularDistance(pd, ap, aDir.negate()))
                    addEdgePerpendicularDistance(pd[0] = -pd[0]);
                cache.aPerpendicularDistance = pd[0];
            }
            if (bdd > 0) {
                var pd = new double[]{tempDist.distance};
                if (getPerpendicularDistance(pd, bp, bDir))
                    addEdgePerpendicularDistance(pd[0]);
                cache.bPerpendicularDistance = pd[0];
            }
            cache.aDomainDistance = add;
            cache.bDomainDistance = bdd;
        }
    }

    public double distance() {
        return computeDistance(p);
    }
}