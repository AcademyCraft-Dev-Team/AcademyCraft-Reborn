package org.academy.api.client.gui.msdf.core;

public class LinearSegment extends EdgeSegment {
    public static final int EDGE_TYPE = 1;

    public Point2[] p = new Point2[2];

    public LinearSegment(Point2 p0, Point2 p1, int edgeColor) {
        super(edgeColor);
        p[0] = p0;
        p[1] = p1;
    }

    @Override
    public LinearSegment clone() {
        return new LinearSegment(p[0], p[1], color);
    }

    @Override
    public int type() {
        return EDGE_TYPE;
    }

    @Override
    public Point2[] controlPoints() {
        return p;
    }

    @Override
    public Point2 point(double param) {
        return new Point2(
                Arithmetic.mix(p[0].x, p[1].x, param),
                Arithmetic.mix(p[0].y, p[1].y, param)
        );
    }

    @Override
    public Vector2 direction(double param) {
        return Vector2.subtract(p[1], p[0]);
    }

    @Override
    public Vector2 directionChange(double param) {
        return new Vector2();
    }

    @Override
    public double length() {
        return Vector2.subtract(p[1], p[0]).length();
    }

    @Override
    public void signedDistance(Point2 origin, double[] param, SignedDistance out, double[] rootsCache) {
        var aqX = origin.x - p[0].x;
        var aqY = origin.y - p[0].y;
        var abX = p[1].x - p[0].x;
        var abY = p[1].y - p[0].y;

        var abLenSq = abX * abX + abY * abY;
        param[0] = (aqX * abX + aqY * abY) / abLenSq;

        double eqX, eqY;
        if (param[0] > 0.5) {
            eqX = p[1].x - origin.x;
            eqY = p[1].y - origin.y;
        } else {
            eqX = p[0].x - origin.x;
            eqY = p[0].y - origin.y;
        }

        var endpointDistance = Math.sqrt(eqX * eqX + eqY * eqY);

        if (param[0] > 0 && param[0] < 1) {
            var abLen = Math.sqrt(abLenSq);
            var orthoX = abY / abLen;
            var orthoY = -abX / abLen;
            var orthoDistance = orthoX * aqX + orthoY * aqY;
            if (Math.abs(orthoDistance) < endpointDistance) {
                out.distance = orthoDistance;
                out.dot = 0;
                return;
            }
        }

        var crossProd = aqX * abY - aqY * abX;
        var dist = Arithmetic.nonZeroSign(crossProd) * endpointDistance;

        double abNormX = 0, abNormY = 0;
        if (abLenSq > 0) {
            var len = Math.sqrt(abLenSq);
            abNormX = abX / len;
            abNormY = abY / len;
        }

        double eqNormX = 0, eqNormY = 0;
        if (endpointDistance > 0) {
            eqNormX = eqX / endpointDistance;
            eqNormY = eqY / endpointDistance;
        }

        var dot = Math.abs(abNormX * eqNormX + abNormY * eqNormY);

        out.distance = dist;
        out.dot = dot;
    }

    @Override
    public int scanlineIntersections(double[] x, int[] dy, double y) {
        if ((y >= p[0].y && y < p[1].y) || (y >= p[1].y && y < p[0].y)) {
            var param = (y - p[0].y) / (p[1].y - p[0].y);
            x[0] = Arithmetic.mix(p[0].x, p[1].x, param);
            dy[0] = Arithmetic.sign(p[1].y - p[0].y);
            return 1;
        }
        return 0;
    }

    @Override
    public void bound(double[] bounds) {
        double xMin = bounds[0], yMin = bounds[1], xMax = bounds[2], yMax = bounds[3];
        if (p[0].x < xMin) xMin = p[0].x;
        if (p[0].y < yMin) yMin = p[0].y;
        if (p[0].x > xMax) xMax = p[0].x;
        if (p[0].y > yMax) yMax = p[0].y;

        if (p[1].x < xMin) xMin = p[1].x;
        if (p[1].y < yMin) yMin = p[1].y;
        if (p[1].x > xMax) xMax = p[1].x;
        if (p[1].y > yMax) yMax = p[1].y;

        bounds[0] = xMin;
        bounds[1] = yMin;
        bounds[2] = xMax;
        bounds[3] = yMax;
    }

    @Override
    public void reverse() {
        var tmp = p[0];
        p[0] = p[1];
        p[1] = tmp;
    }

    @Override
    public void moveStartPoint(Point2 to) {
        p[0] = to;
    }

    @Override
    public void moveEndPoint(Point2 to) {
        p[1] = to;
    }

    @Override
    public EdgeSegment[] splitInThirds() {
        var p1 = point(1.0 / 3.0);
        var p2 = point(2.0 / 3.0);
        return new EdgeSegment[]{
                new LinearSegment(p[0], p1, color),
                new LinearSegment(p1, p2, color),
                new LinearSegment(p2, p[1], color)
        };
    }
}