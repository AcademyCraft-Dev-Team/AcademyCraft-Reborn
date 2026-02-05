package org.academy.api.client.gui.msdf.core;

public class QuadraticSegment extends EdgeSegment {
    public static final int EDGE_TYPE = 2;

    public Point2[] p = new Point2[3];

    public QuadraticSegment(Point2 p0, Point2 p1, Point2 p2, int edgeColor) {
        super(edgeColor);
        p[0] = p0;
        p[1] = p1;
        p[2] = p2;
    }

    @Override
    public QuadraticSegment clone() {
        return new QuadraticSegment(p[0], p[1], p[2], color);
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
        var p01 = new Point2(
                Arithmetic.mix(p[0].x, p[1].x, param),
                Arithmetic.mix(p[0].y, p[1].y, param)
        );
        var p12 = new Point2(
                Arithmetic.mix(p[1].x, p[2].x, param),
                Arithmetic.mix(p[1].y, p[2].y, param)
        );
        return new Point2(
                Arithmetic.mix(p01.x, p12.x, param),
                Arithmetic.mix(p01.y, p12.y, param)
        );
    }

    @Override
    public Vector2 direction(double param) {
        var tangent = new Vector2(
                Arithmetic.mix(p[1].x - p[0].x, p[2].x - p[1].x, param),
                Arithmetic.mix(p[1].y - p[0].y, p[2].y - p[1].y, param)
        );
        if (tangent.x == 0 && tangent.y == 0) {
            return Vector2.subtract(p[2], p[0]);
        }
        return tangent;
    }

    @Override
    public Vector2 directionChange(double param) {
        return Vector2.subtract(Vector2.subtract(p[2], p[1]), Vector2.subtract(p[1], p[0]));
    }

    @Override
    public double length() {
        var ab = Vector2.subtract(p[1], p[0]);
        var br = Vector2.subtract(Vector2.subtract(p[2], p[1]), ab);
        var abab = Vector2.dotProduct(ab, ab);
        var abbr = Vector2.dotProduct(ab, br);
        var brbr = Vector2.dotProduct(br, br);
        var abLen = Math.sqrt(abab);
        var brLen = Math.sqrt(brbr);
        var crs = Vector2.crossProduct(ab, br);
        var h = Math.sqrt(abab + abbr + abbr + brbr);
        return (
                brLen * ((abbr + brbr) * h - abbr * abLen) +
                        crs * crs * Math.log((brLen * h + abbr + brbr) / (brLen * abLen + abbr))
        ) / (brbr * brLen);
    }

    @Override
    public void signedDistance(Point2 origin, double[] param, SignedDistance out, double[] rootsCache) {
        var qaX = p[0].x - origin.x;
        var qaY = p[0].y - origin.y;
        var abX = p[1].x - p[0].x;
        var abY = p[1].y - p[0].y;
        var brX = (p[2].x - p[1].x) - abX;
        var brY = (p[2].y - p[1].y) - abY;

        var a = brX * brX + brY * brY;
        var b = 3 * (abX * brX + abY * brY);
        var c = 2 * (abX * abX + abY * abY) + (qaX * brX + qaY * brY);
        var d = qaX * abX + qaY * abY;

        var solutions = EquationSolver.solveCubic(rootsCache, a, b, c, d);

        var epDirX = direction(0).x;
        var epDirY = direction(0).y;

        var crossProd = epDirX * qaY - epDirY * qaX;
        var qaLen = Math.sqrt(qaX * qaX + qaY * qaY);
        var minDistance = Arithmetic.nonZeroSign(crossProd) * qaLen;

        param[0] = -(qaX * epDirX + qaY * epDirY) / (epDirX * epDirX + epDirY * epDirY);

        var p2OriginX = p[2].x - origin.x;
        var p2OriginY = p[2].y - origin.y;
        var distanceEnd = Math.sqrt(p2OriginX * p2OriginX + p2OriginY * p2OriginY);

        if (distanceEnd < Math.abs(minDistance)) {
            var dir1 = direction(1);
            epDirX = dir1.x;
            epDirY = dir1.y;
            crossProd = epDirX * p2OriginY - epDirY * p2OriginX;
            minDistance = Arithmetic.nonZeroSign(crossProd) * distanceEnd;
            var p1OriginX = p[1].x - origin.x;
            var p1OriginY = p[1].y - origin.y;
            param[0] = (-p1OriginX * epDirX - p1OriginY * epDirY) / (epDirX * epDirX + epDirY * epDirY);
        }

        for (var i = 0; i < solutions; ++i) {
            if (rootsCache[i] > 0 && rootsCache[i] < 1) {
                var qeX = qaX + 2 * rootsCache[i] * abX + rootsCache[i] * rootsCache[i] * brX;
                var qeY = qaY + 2 * rootsCache[i] * abY + rootsCache[i] * rootsCache[i] * brY;
                var distanceQE = Math.sqrt(qeX * qeX + qeY * qeY);
                if (distanceQE <= Math.abs(minDistance)) {
                    var tangentX = abX + rootsCache[i] * brX;
                    var tangentY = abY + rootsCache[i] * brY;
                    crossProd = tangentX * qeY - tangentY * qeX;
                    minDistance = Arithmetic.nonZeroSign(crossProd) * distanceQE;
                    param[0] = rootsCache[i];
                }
            }
        }

        if (param[0] >= 0 && param[0] <= 1) {
            out.distance = minDistance;
            out.dot = 0;
            return;
        }

        Vector2 dirNorm;
        double dot;
        if (param[0] < 0.5) {
            dirNorm = direction(0).normalize(false);
            var qaNormX = qaLen == 0 ? 0 : qaX / qaLen;
            var qaNormY = qaLen == 0 ? 0 : qaY / qaLen;
            dot = Math.abs(dirNorm.x * qaNormX + dirNorm.y * qaNormY);
        } else {
            dirNorm = direction(1).normalize(false);
            var p2NormX = distanceEnd == 0 ? 0 : p2OriginX / distanceEnd;
            var p2NormY = distanceEnd == 0 ? 0 : p2OriginY / distanceEnd;
            dot = Math.abs(dirNorm.x * p2NormX + dirNorm.y * p2NormY);
        }
        out.distance = minDistance;
        out.dot = dot;
    }

    @Override
    public int scanlineIntersections(double[] x, int[] dy, double y) {
        var total = 0;
        var nextDY = y > p[0].y ? 1 : -1;
        x[total] = p[0].x;
        if (p[0].y == y) {
            if (p[0].y < p[1].y || (p[0].y == p[1].y && p[0].y < p[2].y)) {
                dy[total++] = 1;
            } else {
                nextDY = 1;
            }
        }

        var ab = Vector2.subtract(p[1], p[0]);
        var br = Vector2.subtract(Vector2.subtract(p[2], p[1]), ab);
        var t = new double[2];
        var solutions = EquationSolver.solveQuadratic(t, br.y, 2 * ab.y, p[0].y - y);

        if (solutions >= 2 && t[0] > t[1]) {
            var tmp = t[0];
            t[0] = t[1];
            t[1] = tmp;
        }

        for (var i = 0; i < solutions && total < 2; ++i) {
            if (t[i] >= 0 && t[i] <= 1) {
                x[total] = p[0].x + 2 * t[i] * ab.x + t[i] * t[i] * br.x;
                if (nextDY * (ab.y + t[i] * br.y) >= 0) {
                    dy[total++] = nextDY;
                    nextDY = -nextDY;
                }
            }
        }

        if (p[2].y == y) {
            if (nextDY > 0 && total > 0) {
                --total;
                nextDY = -1;
            }
            if ((p[2].y < p[1].y || (p[2].y == p[1].y && p[2].y < p[0].y)) && total < 2) {
                x[total] = p[2].x;
                if (nextDY < 0) {
                    dy[total++] = -1;
                    nextDY = 1;
                }
            }
        }

        if (nextDY != (y >= p[2].y ? 1 : -1)) {
            if (total > 0) {
                --total;
            } else {
                if (Math.abs(p[2].y - y) < Math.abs(p[0].y - y)) {
                    x[total] = p[2].x;
                }
                dy[total++] = nextDY;
            }
        }
        return total;
    }

    @Override
    public void bound(double[] bounds) {
        double xMin = bounds[0], yMin = bounds[1], xMax = bounds[2], yMax = bounds[3];

        if (p[0].x < xMin) xMin = p[0].x;
        if (p[0].y < yMin) yMin = p[0].y;
        if (p[0].x > xMax) xMax = p[0].x;
        if (p[0].y > yMax) yMax = p[0].y;

        if (p[2].x < xMin) xMin = p[2].x;
        if (p[2].y < yMin) yMin = p[2].y;
        if (p[2].x > xMax) xMax = p[2].x;
        if (p[2].y > yMax) yMax = p[2].y;

        var bot = Vector2.subtract(Vector2.subtract(p[1], p[0]), Vector2.subtract(p[2], p[1]));
        if (bot.x != 0) {
            var param = (p[1].x - p[0].x) / bot.x;
            if (param > 0 && param < 1) {
                var pointAtParam = point(param);
                if (pointAtParam.x < xMin) xMin = pointAtParam.x;
                if (pointAtParam.y < yMin) yMin = pointAtParam.y;
                if (pointAtParam.x > xMax) xMax = pointAtParam.x;
                if (pointAtParam.y > yMax) yMax = pointAtParam.y;
            }
        }
        if (bot.y != 0) {
            var param = (p[1].y - p[0].y) / bot.y;
            if (param > 0 && param < 1) {
                var pointAtParam = point(param);
                if (pointAtParam.x < xMin) xMin = pointAtParam.x;
                if (pointAtParam.y < yMin) yMin = pointAtParam.y;
                if (pointAtParam.x > xMax) xMax = pointAtParam.x;
                if (pointAtParam.y > yMax) yMax = pointAtParam.y;
            }
        }

        bounds[0] = xMin;
        bounds[1] = yMin;
        bounds[2] = xMax;
        bounds[3] = yMax;
    }

    @Override
    public void reverse() {
        var tmp = p[0];
        p[0] = p[2];
        p[2] = tmp;
    }

    @Override
    public void moveStartPoint(Point2 to) {
        var origSDir = Vector2.subtract(p[0], p[1]);
        var origP1 = p[1];
        var p2p1 = Vector2.subtract(p[2], p[1]);
        var crossProd = Vector2.crossProduct(Vector2.subtract(p[0], p[1]), Vector2.subtract(to, p[0]));
        var denom = Vector2.crossProduct(Vector2.subtract(p[0], p[1]), Vector2.subtract(p[2], p[1]));
        if (denom != 0) {
            var factor = crossProd / denom;
            p[1] = new Point2(p[1].x + factor * p2p1.x, p[1].y + factor * p2p1.y);
        }
        p[0] = to;
        if (Vector2.dotProduct(origSDir, Vector2.subtract(p[0], p[1])) < 0) {
            p[1] = origP1;
        }
    }

    @Override
    public void moveEndPoint(Point2 to) {
        var origEDir = Vector2.subtract(p[2], p[1]);
        var origP1 = p[1];
        var p0p1 = Vector2.subtract(p[0], p[1]);
        var crossProd = Vector2.crossProduct(Vector2.subtract(p[2], p[1]), Vector2.subtract(to, p[2]));
        var denom = Vector2.crossProduct(Vector2.subtract(p[2], p[1]), Vector2.subtract(p[0], p[1]));
        if (denom != 0) {
            var factor = crossProd / denom;
            p[1] = new Point2(p[1].x + factor * p0p1.x, p[1].y + factor * p0p1.y);
        }
        p[2] = to;
        if (Vector2.dotProduct(origEDir, Vector2.subtract(p[2], p[1])) < 0) {
            p[1] = origP1;
        }
    }

    @Override
    public EdgeSegment[] splitInThirds() {
        var p0 = p[0];
        var p1 = p[1];
        var p2 = p[2];

        var p01 = new Point2(
                Arithmetic.mix(p0.x, p1.x, 1.0 / 3.0),
                Arithmetic.mix(p0.y, p1.y, 1.0 / 3.0)
        );
        var p12 = new Point2(
                Arithmetic.mix(p1.x, p2.x, 2.0 / 3.0),
                Arithmetic.mix(p1.y, p2.y, 2.0 / 3.0)
        );

        var p01_5_9 = new Point2(
                Arithmetic.mix(p0.x, p1.x, 5.0 / 9.0),
                Arithmetic.mix(p0.y, p1.y, 5.0 / 9.0)
        );
        var p12_4_9 = new Point2(
                Arithmetic.mix(p1.x, p2.x, 4.0 / 9.0),
                Arithmetic.mix(p1.y, p2.y, 4.0 / 9.0)
        );
        var pMiddleControl = new Point2(
                Arithmetic.mix(p01_5_9.x, p12_4_9.x, 0.5),
                Arithmetic.mix(p01_5_9.y, p12_4_9.y, 0.5)
        );

        return new EdgeSegment[]{
                new QuadraticSegment(p0, p01, point(1.0 / 3.0), color),
                new QuadraticSegment(point(1.0 / 3.0), pMiddleControl, point(2.0 / 3.0), color),
                new QuadraticSegment(point(2.0 / 3.0), p12, p2, color)
        };
    }

    public CubicSegment convertToCubic() {
        return new CubicSegment(
                p[0],
                new Point2(
                        Arithmetic.mix(p[0].x, p[1].x, 2.0 / 3.0),
                        Arithmetic.mix(p[0].y, p[1].y, 2.0 / 3.0)
                ),
                new Point2(
                        Arithmetic.mix(p[1].x, p[2].x, 1.0 / 3.0),
                        Arithmetic.mix(p[1].y, p[2].y, 1.0 / 3.0)
                ),
                p[2],
                color
        );
    }
}