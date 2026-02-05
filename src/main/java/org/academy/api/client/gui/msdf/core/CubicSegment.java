package org.academy.api.client.gui.msdf.core;

public class CubicSegment extends EdgeSegment {
    public static final int EDGE_TYPE = 3;

    public Point2[] p = new Point2[4];

    public CubicSegment(Point2 p0, Point2 p1, Point2 p2, Point2 p3, int edgeColor) {
        super(edgeColor);
        p[0] = p0;
        p[1] = p1;
        p[2] = p2;
        p[3] = p3;
    }

    @Override
    public CubicSegment clone() {
        return new CubicSegment(p[0], p[1], p[2], p[3], color);
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
        var p23 = new Point2(
                Arithmetic.mix(p[2].x, p[3].x, param),
                Arithmetic.mix(p[2].y, p[3].y, param)
        );
        var p012 = new Point2(
                Arithmetic.mix(p01.x, p12.x, param),
                Arithmetic.mix(p01.y, p12.y, param)
        );
        var p123 = new Point2(
                Arithmetic.mix(p12.x, p23.x, param),
                Arithmetic.mix(p12.y, p23.y, param)
        );
        return new Point2(
                Arithmetic.mix(p012.x, p123.x, param),
                Arithmetic.mix(p012.y, p123.y, param)
        );
    }

    @Override
    public Vector2 direction(double param) {
        var tangent = new Vector2(
                Arithmetic.mix(
                        Arithmetic.mix(p[1].x - p[0].x, p[2].x - p[1].x, param),
                        Arithmetic.mix(p[2].x - p[1].x, p[3].x - p[2].x, param),
                        param
                ),
                Arithmetic.mix(
                        Arithmetic.mix(p[1].y - p[0].y, p[2].y - p[1].y, param),
                        Arithmetic.mix(p[2].y - p[1].y, p[3].y - p[2].y, param),
                        param
                )
        );
        if (tangent.x == 0 && tangent.y == 0) {
            if (param == 0) return Vector2.subtract(p[2], p[0]);
            if (param == 1) return Vector2.subtract(p[3], p[1]);
        }
        return tangent;
    }

    @Override
    public Vector2 directionChange(double param) {
        return new Vector2(
                Arithmetic.mix(
                        (p[2].x - p[1].x) - (p[1].x - p[0].x),
                        (p[3].x - p[2].x) - (p[2].x - p[1].x),
                        param
                ),
                Arithmetic.mix(
                        (p[2].y - p[1].y) - (p[1].y - p[0].y),
                        (p[3].y - p[2].y) - (p[2].y - p[1].y),
                        param
                )
        );
    }

    @Override
    public void signedDistance(Point2 origin, double[] param, SignedDistance out, double[] rootsCache) {
        var qaX = p[0].x - origin.x;
        var qaY = p[0].y - origin.y;
        var abX = p[1].x - p[0].x;
        var abY = p[1].y - p[0].y;
        var brX = (p[2].x - p[1].x) - abX;
        var brY = (p[2].y - p[1].y) - abY;
        var asX = (p[3].x - p[2].x) - (p[2].x - p[1].x) - brX;
        var asY = (p[3].y - p[2].y) - (p[2].y - p[1].y) - brY;

        var epDirX = direction(0).x;
        var epDirY = direction(0).y;

        var crossProd = epDirX * qaY - epDirY * qaX;
        var qaLen = Math.sqrt(qaX * qaX + qaY * qaY);
        var minDistance = Arithmetic.nonZeroSign(crossProd) * qaLen;

        param[0] = -(qaX * epDirX + qaY * epDirY) / (epDirX * epDirX + epDirY * epDirY);

        var p3OriginX = p[3].x - origin.x;
        var p3OriginY = p[3].y - origin.y;
        var distanceEnd = Math.sqrt(p3OriginX * p3OriginX + p3OriginY * p3OriginY);

        if (distanceEnd < Math.abs(minDistance)) {
            var dir1 = direction(1);
            epDirX = dir1.x;
            epDirY = dir1.y;
            crossProd = epDirX * p3OriginY - epDirY * p3OriginX;
            minDistance = Arithmetic.nonZeroSign(crossProd) * distanceEnd;
            var dotEp = epDirX * epDirX + epDirY * epDirY;
            var dotDiff = p3OriginX * epDirX + p3OriginY * epDirY;
            param[0] = (dotEp - dotDiff) / dotEp;
        }

        for (var i = 0; i <= MsdfBase.MSDFGEN_CUBIC_SEARCH_STARTS; ++i) {
            var t = (double) i / MsdfBase.MSDFGEN_CUBIC_SEARCH_STARTS;
            var qeX = qaX + 3 * t * abX + 3 * t * t * brX + t * t * t * asX;
            var qeY = qaY + 3 * t * abY + 3 * t * t * brY + t * t * t * asY;

            var d1X = 3 * abX + 6 * t * brX + 3 * t * t * asX;
            var d1Y = 3 * abY + 6 * t * brY + 3 * t * t * asY;

            var d2X = 6 * brX + 6 * t * asX;
            var d2Y = 6 * brY + 6 * t * asY;

            var improvedT = t - (qeX * d1X + qeY * d1Y) / ((d1X * d1X + d1Y * d1Y) + (qeX * d2X + qeY * d2Y));
            if (improvedT > 0 && improvedT < 1) {
                var remainingSteps = MsdfBase.MSDFGEN_CUBIC_SEARCH_STEPS;
                do {
                    t = improvedT;
                    qeX = qaX + 3 * t * abX + 3 * t * t * brX + t * t * t * asX;
                    qeY = qaY + 3 * t * abY + 3 * t * t * brY + t * t * t * asY;
                    d1X = 3 * abX + 6 * t * brX + 3 * t * t * asX;
                    d1Y = 3 * abY + 6 * t * brY + 3 * t * t * asY;
                    if (--remainingSteps == 0) break;
                    d2X = 6 * brX + 6 * t * asX;
                    d2Y = 6 * brY + 6 * t * asY;
                    improvedT = t - (qeX * d1X + qeY * d1Y) / ((d1X * d1X + d1Y * d1Y) + (qeX * d2X + qeY * d2Y));
                } while (improvedT > 0 && improvedT < 1);

                var distanceQE = Math.sqrt(qeX * qeX + qeY * qeY);
                if (distanceQE < Math.abs(minDistance)) {
                    crossProd = d1X * qeY - d1Y * qeX;
                    minDistance = Arithmetic.nonZeroSign(crossProd) * distanceQE;
                    param[0] = t;
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
            var p3NormX = distanceEnd == 0 ? 0 : p3OriginX / distanceEnd;
            var p3NormY = distanceEnd == 0 ? 0 : p3OriginY / distanceEnd;
            dot = Math.abs(dirNorm.x * p3NormX + dirNorm.y * p3NormY);
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
            if (p[0].y < p[1].y || (p[0].y == p[1].y && (p[0].y < p[2].y || (p[0].y == p[2].y && p[0].y < p[3].y)))) {
                dy[total++] = 1;
            } else {
                nextDY = 1;
            }
        }

        var ab = Vector2.subtract(p[1], p[0]);
        var br = Vector2.subtract(Vector2.subtract(p[2], p[1]), ab);
        var as = Vector2.subtract(Vector2.subtract(Vector2.subtract(p[3], p[2]), Vector2.subtract(p[2], p[1])), br);
        var t = new double[3];
        var solutions = EquationSolver.solveCubic(t, as.y, 3 * br.y, 3 * ab.y, p[0].y - y);

        if (solutions >= 2) {
            if (t[0] > t[1]) {
                var tmp = t[0];
                t[0] = t[1];
                t[1] = tmp;
            }
            if (solutions >= 3 && t[1] > t[2]) {
                var tmp = t[1];
                t[1] = t[2];
                t[2] = tmp;
                if (t[0] > t[1]) {
                    tmp = t[0];
                    t[0] = t[1];
                    t[1] = tmp;
                }
            }
        }

        for (var i = 0; i < solutions && total < 3; ++i) {
            if (t[i] >= 0 && t[i] <= 1) {
                x[total] = p[0].x + 3 * t[i] * ab.x + 3 * t[i] * t[i] * br.x + t[i] * t[i] * t[i] * as.x;
                var derivative = new Vector2(
                        3 * ab.x + 6 * t[i] * br.x + 3 * t[i] * t[i] * as.x,
                        3 * ab.y + 6 * t[i] * br.y + 3 * t[i] * t[i] * as.y
                );
                if (nextDY * derivative.y >= 0) {
                    dy[total++] = nextDY;
                    nextDY = -nextDY;
                }
            }
        }

        if (p[3].y == y) {
            if (nextDY > 0 && total > 0) {
                --total;
                nextDY = -1;
            }
            if ((p[3].y < p[2].y || (p[3].y == p[2].y && (p[3].y < p[1].y || (p[3].y == p[1].y && p[3].y < p[0].y)))) && total < 3) {
                x[total] = p[3].x;
                if (nextDY < 0) {
                    dy[total++] = -1;
                    nextDY = 1;
                }
            }
        }

        if (nextDY != (y >= p[3].y ? 1 : -1)) {
            if (total > 0) {
                --total;
            } else {
                if (Math.abs(p[3].y - y) < Math.abs(p[0].y - y)) {
                    x[total] = p[3].x;
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

        if (p[3].x < xMin) xMin = p[3].x;
        if (p[3].y < yMin) yMin = p[3].y;
        if (p[3].x > xMax) xMax = p[3].x;
        if (p[3].y > yMax) yMax = p[3].y;

        var a0 = Vector2.subtract(p[1], p[0]);
        var a1 = Vector2.multiply(Vector2.subtract(Vector2.subtract(p[2], p[1]), a0), 2.0);
        var a2 = new Vector2(
                p[3].x - 3 * p[2].x + 3 * p[1].x - p[0].x,
                p[3].y - 3 * p[2].y + 3 * p[1].y - p[0].y
        );

        var params = new double[2];
        var solutions = EquationSolver.solveQuadratic(params, a2.x, a1.x, a0.x);
        for (var i = 0; i < solutions; ++i) {
            if (params[i] > 0 && params[i] < 1) {
                var pointAtParam = point(params[i]);
                if (pointAtParam.x < xMin) xMin = pointAtParam.x;
                if (pointAtParam.y < yMin) yMin = pointAtParam.y;
                if (pointAtParam.x > xMax) xMax = pointAtParam.x;
                if (pointAtParam.y > yMax) yMax = pointAtParam.y;
            }
        }

        solutions = EquationSolver.solveQuadratic(params, a2.y, a1.y, a0.y);
        for (var i = 0; i < solutions; ++i) {
            if (params[i] > 0 && params[i] < 1) {
                var pointAtParam = point(params[i]);
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
        p[0] = p[3];
        p[3] = tmp;
        tmp = p[1];
        p[1] = p[2];
        p[2] = tmp;
    }

    @Override
    public void moveStartPoint(Point2 to) {
        p[1] = new Point2(p[1].x + to.x - p[0].x, p[1].y + to.y - p[0].y);
        p[0] = to;
    }

    @Override
    public void moveEndPoint(Point2 to) {
        p[2] = new Point2(p[2].x + to.x - p[3].x, p[2].y + to.y - p[3].y);
        p[3] = to;
    }

    @Override
    public EdgeSegment[] splitInThirds() {
        var p0 = p[0];
        var p1 = p[1];
        var p2 = p[2];
        var p3 = p[3];

        var p01 = p0.equals(p1) ? p0 : new Point2(
                Arithmetic.mix(p0.x, p1.x, 1.0 / 3.0),
                Arithmetic.mix(p0.y, p1.y, 1.0 / 3.0)
        );
        var p12 = new Point2(
                Arithmetic.mix(p1.x, p2.x, 1.0 / 3.0),
                Arithmetic.mix(p1.y, p2.y, 1.0 / 3.0)
        );
        var p23 = new Point2(
                Arithmetic.mix(p2.x, p3.x, 1.0 / 3.0),
                Arithmetic.mix(p2.y, p3.y, 1.0 / 3.0)
        );

        var p012 = new Point2(
                Arithmetic.mix(p01.x, p12.x, 1.0 / 3.0),
                Arithmetic.mix(p01.y, p12.y, 1.0 / 3.0)
        );
        var p123 = new Point2(
                Arithmetic.mix(p12.x, p23.x, 1.0 / 3.0),
                Arithmetic.mix(p12.y, p23.y, 1.0 / 3.0)
        );

        var p0_2 = point(1.0 / 3.0);
        var p1_2 = point(2.0 / 3.0);

        var p01_2 = new Point2(
                Arithmetic.mix(p0.x, p1.x, 2.0 / 3.0),
                Arithmetic.mix(p0.y, p1.y, 2.0 / 3.0)
        );
        var p12_2 = new Point2(
                Arithmetic.mix(p1.x, p2.x, 2.0 / 3.0),
                Arithmetic.mix(p1.y, p2.y, 2.0 / 3.0)
        );
        var p23_2 = new Point2(
                Arithmetic.mix(p2.x, p3.x, 2.0 / 3.0),
                Arithmetic.mix(p2.y, p3.y, 2.0 / 3.0)
        );

        var p012_2 = new Point2(
                Arithmetic.mix(p01_2.x, p12_2.x, 2.0 / 3.0),
                Arithmetic.mix(p01_2.y, p12_2.y, 2.0 / 3.0)
        );
        var p123_2 = new Point2(
                Arithmetic.mix(p12_2.x, p23_2.x, 2.0 / 3.0),
                Arithmetic.mix(p12_2.y, p23_2.y, 2.0 / 3.0)
        );

        return new EdgeSegment[]{
                new CubicSegment(p0, p01, p012, p0_2, color),
                new CubicSegment(p0_2,
                        new Point2(Arithmetic.mix(p012.x, p123.x, 2.0 / 3.0), Arithmetic.mix(p012.y, p123.y, 2.0 / 3.0)),
                        new Point2(Arithmetic.mix(p012_2.x, p123_2.x, 1.0 / 3.0), Arithmetic.mix(p012_2.y, p123_2.y, 1.0 / 3.0)),
                        p1_2, color),
                new CubicSegment(p1_2, p123_2, p23_2, p3, color)
        };
    }
}