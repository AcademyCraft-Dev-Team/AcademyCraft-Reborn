package org.academy.api.client.gui.msdf.core;

public class MultiDistanceSelector implements EdgeSelector {
    private final PerpendicularDistanceSelectorBase r = new PerpendicularDistanceSelectorBase() {
        @Override
        public void reset(Point2 p) {}

        @Override
        public void addEdge(EdgeSegment prev, EdgeSegment curr, EdgeSegment next) {}
    };
    private final PerpendicularDistanceSelectorBase g = new PerpendicularDistanceSelectorBase() {
        @Override
        public void reset(Point2 p) {}

        @Override
        public void addEdge(EdgeSegment prev, EdgeSegment curr, EdgeSegment next) {}
    };
    private final PerpendicularDistanceSelectorBase b = new PerpendicularDistanceSelectorBase() {
        @Override
        public void reset(Point2 p) {}

        @Override
        public void addEdge(EdgeSegment prev, EdgeSegment curr, EdgeSegment next) {}
    };
    private final SignedDistance tempDist = new SignedDistance();
    private final double[] paramCache = new double[1];
    private final double[] rootsCache = new double[3];
    private Point2 p = new Point2();

    @Override
    public void reset(Point2 p) {
        var delta = PerpendicularDistanceSelectorBase.DISTANCE_DELTA_FACTOR * Vector2.subtract(p, this.p).length();
        r.reset(delta);
        g.reset(delta);
        b.reset(delta);
        this.p = p;
    }

    @Override
    public void addEdge(EdgeSegment prevEdge, EdgeSegment edge, EdgeSegment nextEdge) {
        var cache = r.getNextCache();
        g.edgeIndex++;
        b.edgeIndex++;

        var edgeColor = edge.color;
        var redChannel = (edgeColor & EdgeColor.RED) != 0;
        var greenChannel = (edgeColor & EdgeColor.GREEN) != 0;
        var blueChannel = (edgeColor & EdgeColor.BLUE) != 0;

        var redRelevant = redChannel && r.isEdgeRelevant(cache, edge, p);
        var greenRelevant = greenChannel && g.isEdgeRelevant(cache, edge, p);
        var blueRelevant = blueChannel && b.isEdgeRelevant(cache, edge, p);

        if (!redRelevant && !greenRelevant && !blueRelevant) return;

        edge.signedDistance(p, paramCache, tempDist, rootsCache);
        var param = paramCache[0];

        if (redRelevant) r.addEdgeTrueDistance(edge, tempDist, param);
        if (greenRelevant) g.addEdgeTrueDistance(edge, tempDist, param);
        if (blueRelevant) b.addEdgeTrueDistance(edge, tempDist, param);

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
            if (PerpendicularDistanceSelectorBase.getPerpendicularDistance(pd, ap, aDir.negate())) {
                pd[0] = -pd[0];
                if (redChannel) r.addEdgePerpendicularDistance(pd[0]);
                if (greenChannel) g.addEdgePerpendicularDistance(pd[0]);
                if (blueChannel) b.addEdgePerpendicularDistance(pd[0]);
            }
            cache.aPerpendicularDistance = pd[0];
        }

        if (bdd > 0) {
            var pd = new double[]{tempDist.distance};
            if (PerpendicularDistanceSelectorBase.getPerpendicularDistance(pd, bp, bDir)) {
                if (redChannel) r.addEdgePerpendicularDistance(pd[0]);
                if (greenChannel) g.addEdgePerpendicularDistance(pd[0]);
                if (blueChannel) b.addEdgePerpendicularDistance(pd[0]);
            }
            cache.bPerpendicularDistance = pd[0];
        }

        cache.aDomainDistance = add;
        cache.bDomainDistance = bdd;
    }

    @Override
    public void merge(EdgeSelector other) {
        if (other instanceof MultiDistanceSelector o) {
            r.merge(o.r);
            g.merge(o.g);
            b.merge(o.b);
        }
    }

    public MultiDistance distance() {
        var dist = new MultiDistance();
        dist.r = r.computeDistance(p);
        dist.g = g.computeDistance(p);
        dist.b = b.computeDistance(p);
        return dist;
    }

    public SignedDistance trueDistance() {
        var dist = r.trueDistance();
        if (SignedDistance.lessThan(g.trueDistance(), dist)) dist = g.trueDistance();
        if (SignedDistance.lessThan(b.trueDistance(), dist)) dist = b.trueDistance();
        return dist;
    }
}