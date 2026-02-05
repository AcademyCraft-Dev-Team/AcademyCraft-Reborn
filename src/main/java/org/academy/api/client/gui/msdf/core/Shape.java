package org.academy.api.client.gui.msdf.core;

import java.util.ArrayList;
import java.util.List;

public class Shape {
    private static final double DECONVERGE_OVERSHOOT = 1.11111111111111111;
    public List<Contour> contours = new ArrayList<>();
    public boolean inverseYAxis;

    public Shape() {
        inverseYAxis = false;
    }

    private static void deconvergeEdge(EdgeHolder edgeHolder, int param, Vector2 vector) {
        var edge = edgeHolder.get();
        if (edge == null) return;

        if (edge.type() == QuadraticSegment.EDGE_TYPE) {
            var quad = (QuadraticSegment) edge;
            edgeHolder.assign(new EdgeHolder(quad.convertToCubic()));
            edge = edgeHolder.get();
        }

        if (edge.type() == CubicSegment.EDGE_TYPE) {
            var cubic = (CubicSegment) edge;
            var p = cubic.controlPoints();
            switch (param) {
                case 0:
                    var dir0 = Vector2.subtract(p[1], p[0]);
                    var len0 = dir0.length();
                    if (len0 > 0) {
                        p[1] = new Point2(
                                p[1].x + len0 * vector.x,
                                p[1].y + len0 * vector.y
                        );
                    }
                    break;
                case 1:
                    var dir1 = Vector2.subtract(p[2], p[3]);
                    var len1 = dir1.length();
                    if (len1 > 0) {
                        p[2] = new Point2(
                                p[2].x + len1 * vector.x,
                                p[2].y + len1 * vector.y
                        );
                    }
                    break;
            }
        }
    }

    public void addContour(Contour contour) {
        contours.add(contour);
    }

    public Contour addContour() {
        var newContour = new Contour();
        contours.add(newContour);
        return newContour;
    }

    public boolean validate() {
        for (var contour : contours) {
            if (!contour.edges.isEmpty()) {
                var corner = contour.edges.getLast().get().point(1);
                for (var edgeHolder : contour.edges) {
                    var edge = edgeHolder.get();
                    if (edge == null) return false;
                    if (!edge.point(0).equals(corner)) return false;
                    corner = edge.point(1);
                }
            }
        }
        return true;
    }

    public void normalize() {
        for (var contour : contours) {
            if (contour.edges.size() == 1) {
                var parts = contour.edges.getFirst().get().splitInThirds();
                contour.edges.clear();
                for (var part : parts) {
                    contour.edges.add(new EdgeHolder(part));
                }
            } else if (!contour.edges.isEmpty()) {
                var prevEdgeHolder = contour.edges.getLast();
                for (var edgeHolder : contour.edges) {
                    var prevDir = prevEdgeHolder.get().direction(1).normalize(false);
                    var curDir = edgeHolder.get().direction(0).normalize(false);
                    if (Vector2.dotProduct(prevDir, curDir) < MsdfBase.MSDFGEN_CORNER_DOT_EPSILON - 1) {
                        var factor = DECONVERGE_OVERSHOOT * Math.sqrt(1 - (MsdfBase.MSDFGEN_CORNER_DOT_EPSILON - 1) * (MsdfBase.MSDFGEN_CORNER_DOT_EPSILON - 1)) / (MsdfBase.MSDFGEN_CORNER_DOT_EPSILON - 1);
                        var axis = Vector2.multiply(Vector2.subtract(curDir, prevDir).normalize(false), factor);
                        if (ConvergentCurveOrdering.convergentCurveOrdering(prevEdgeHolder.get(), edgeHolder.get()) < 0) {
                            axis = axis.negate();
                        }
                        deconvergeEdge(prevEdgeHolder, 1, axis.getOrthogonal(true));
                        deconvergeEdge(edgeHolder, 0, axis.getOrthogonal(false));
                    }
                    prevEdgeHolder = edgeHolder;
                }
            }
        }
    }

    public void bound(double[] bounds) {
        for (var contour : contours) {
            contour.bound(bounds);
        }
    }

    public void boundMiters(double[] bounds, double border, double miterLimit, int polarity) {
        for (var contour : contours) {
            contour.boundMiters(bounds, border, miterLimit, polarity);
        }
    }

    public Bounds getBounds(double border, double miterLimit, int polarity) {
        final var LARGE_VALUE = 1e240;
        var bounds = new Bounds(LARGE_VALUE, LARGE_VALUE, -LARGE_VALUE, -LARGE_VALUE);
        var boundArray = new double[]{bounds.l, bounds.b, bounds.r, bounds.t};
        bound(boundArray);
        bounds.l = boundArray[0];
        bounds.b = boundArray[1];
        bounds.r = boundArray[2];
        bounds.t = boundArray[3];

        if (border > 0) {
            bounds.l -= border;
            bounds.b -= border;
            bounds.r += border;
            bounds.t += border;
            if (miterLimit > 0) {
                boundArray = new double[]{bounds.l, bounds.b, bounds.r, bounds.t};
                boundMiters(boundArray, border, miterLimit, polarity);
                bounds.l = boundArray[0];
                bounds.b = boundArray[1];
                bounds.r = boundArray[2];
                bounds.t = boundArray[3];
            }
        }
        return bounds;
    }

    public void scanline(Scanline line, double y) {
        List<Scanline.Intersection> intersections = new ArrayList<>();
        var x = new double[3];
        var dy = new int[3];
        for (var contour : contours) {
            for (var edgeHolder : contour.edges) {
                var edge = edgeHolder.get();
                var n = edge.scanlineIntersections(x, dy, y);
                for (var i = 0; i < n; ++i) {
                    intersections.add(new Scanline.Intersection(x[i], dy[i]));
                }
            }
        }
        line.setIntersections(intersections);
    }

    public int edgeCount() {
        var total = 0;
        for (var contour : contours) {
            total += contour.edges.size();
        }
        return total;
    }

    public void orientContours() {
        class Intersection {
            double x;
            int direction;
            int contourIndex;
        }

        List<Integer> orientations = new ArrayList<>();
        for (var i = 0; i < contours.size(); ++i) {
            orientations.add(0);
        }

        var ratio = 0.5 * (Math.sqrt(5) - 1);

        for (var i = 0; i < contours.size(); ++i) {
            if (orientations.get(i) == 0 && !contours.get(i).edges.isEmpty()) {
                var y0 = contours.get(i).edges.getFirst().get().point(0).y;
                var y1 = y0;
                for (var edgeHolder : contours.get(i).edges) {
                    if (y0 == y1) {
                        y1 = edgeHolder.get().point(1).y;
                    }
                }
                for (var edgeHolder : contours.get(i).edges) {
                    if (y0 == y1) {
                        y1 = edgeHolder.get().point(ratio).y;
                    }
                }
                var y = Arithmetic.mix(y0, y1, ratio);

                List<Intersection> intersections = new ArrayList<>();
                for (var j = 0; j < contours.size(); ++j) {
                    for (var edgeHolder : contours.get(j).edges) {
                        var xArr = new double[3];
                        var dyArr = new int[3];
                        var n = edgeHolder.get().scanlineIntersections(xArr, dyArr, y);
                        for (var k = 0; k < n; ++k) {
                            var intersection = new Intersection();
                            intersection.x = xArr[k];
                            intersection.direction = dyArr[k];
                            intersection.contourIndex = j;
                            intersections.add(intersection);
                        }
                    }
                }

                if (!intersections.isEmpty()) {
                    intersections.sort((a, b) -> Arithmetic.sign(a.x - b.x));

                    for (var j = 1; j < intersections.size(); ++j) {
                        if (intersections.get(j).x == intersections.get(j - 1).x) {
                            intersections.get(j).direction = 0;
                            intersections.get(j - 1).direction = 0;
                        }
                    }

                    for (var j = 0; j < intersections.size(); ++j) {
                        if (intersections.get(j).direction != 0) {
                            var index = intersections.get(j).contourIndex;
                            int value = orientations.get(index);
                            value += 2 * ((j & 1) ^ (intersections.get(j).direction > 0 ? 1 : 0)) - 1;
                            orientations.set(index, value);
                        }
                    }
                }
            }
        }

        for (var i = 0; i < contours.size(); ++i) {
            if (orientations.get(i) < 0) {
                contours.get(i).reverse();
            }
        }
    }

    public int getYAxisOrientation() {
        return inverseYAxis ? MsdfBase.MSDFGEN_Y_AXIS_NONDEFAULT_ORIENTATION : MsdfBase.MSDFGEN_Y_AXIS_DEFAULT_ORIENTATION;
    }

    public void setYAxisOrientation(int yAxisOrientation) {
        inverseYAxis = yAxisOrientation != MsdfBase.MSDFGEN_Y_AXIS_DEFAULT_ORIENTATION;
    }

    public static class Bounds {
        public double l, b, r, t;

        public Bounds() {
            l = 0;
            b = 0;
            r = 0;
            t = 0;
        }

        public Bounds(double l, double b, double r, double t) {
            this.l = l;
            this.b = b;
            this.r = r;
            this.t = t;
        }
    }
}