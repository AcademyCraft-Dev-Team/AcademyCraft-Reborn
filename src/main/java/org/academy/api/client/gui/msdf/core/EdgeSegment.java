package org.academy.api.client.gui.msdf.core;

public abstract class EdgeSegment {
    public int color;

    public EdgeSegment() {
        color = EdgeColor.WHITE;
    }

    public EdgeSegment(int edgeColor) {
        color = edgeColor;
    }

    public static EdgeSegment create(Point2 p0, Point2 p1, int edgeColor) {
        return new LinearSegment(p0, p1, edgeColor);
    }

    public static EdgeSegment create(Point2 p0, Point2 p1, Point2 p2, int edgeColor) {
        if (Vector2.crossProduct(Vector2.subtract(p1, p0), Vector2.subtract(p2, p1)) == 0) {
            return new LinearSegment(p0, p2, edgeColor);
        }
        return new QuadraticSegment(p0, p1, p2, edgeColor);
    }

    public static EdgeSegment create(Point2 p0, Point2 p1, Point2 p2, Point2 p3, int edgeColor) {
        var p12 = Vector2.subtract(p2, p1);
        if (Vector2.crossProduct(Vector2.subtract(p1, p0), p12) == 0 && Vector2.crossProduct(p12, Vector2.subtract(p3, p2)) == 0) {
            return new LinearSegment(p0, p3, edgeColor);
        }
        var p12_1 = new Point2(1.5 * p1.x - 0.5 * p0.x, 1.5 * p1.y - 0.5 * p0.y);
        var p12_2 = new Point2(1.5 * p2.x - 0.5 * p3.x, 1.5 * p2.y - 0.5 * p3.y);
        if (p12_1.equals(p12_2)) {
            return new QuadraticSegment(p0, p12_1, p3, edgeColor);
        }
        return new CubicSegment(p0, p1, p2, p3, edgeColor);
    }

    public abstract EdgeSegment clone();

    public abstract int type();

    public abstract Point2[] controlPoints();

    public abstract Point2 point(double param);

    public abstract Vector2 direction(double param);

    public abstract Vector2 directionChange(double param);

    public abstract void signedDistance(Point2 origin, double[] param, SignedDistance out, double[] rootsCache);

    public void distanceToPerpendicularDistance(SignedDistance distance, Point2 origin, double param) {
        if (param < 0) {
            var dir = direction(0).normalize(false);
            var aq = Vector2.subtract(origin, point(0));
            var ts = Vector2.dotProduct(aq, dir);
            if (ts < 0) {
                var perpendicularDistance = Vector2.crossProduct(aq, dir);
                if (Math.abs(perpendicularDistance) <= Math.abs(distance.distance)) {
                    distance.distance = perpendicularDistance;
                    distance.dot = 0;
                }
            }
        } else if (param > 1) {
            var dir = direction(1).normalize(false);
            var bq = Vector2.subtract(origin, point(1));
            var ts = Vector2.dotProduct(bq, dir);
            if (ts > 0) {
                var perpendicularDistance = Vector2.crossProduct(bq, dir);
                if (Math.abs(perpendicularDistance) <= Math.abs(distance.distance)) {
                    distance.distance = perpendicularDistance;
                    distance.dot = 0;
                }
            }
        }
    }

    public abstract int scanlineIntersections(double[] x, int[] dy, double y);

    public abstract void bound(double[] bounds);

    public abstract void reverse();

    public abstract void moveStartPoint(Point2 to);

    public abstract void moveEndPoint(Point2 to);

    public abstract EdgeSegment[] splitInThirds();

    public double length() {
        return 0;
    }
}