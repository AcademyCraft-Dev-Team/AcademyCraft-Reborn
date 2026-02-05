package org.academy.api.client.gui.msdf.core;

import java.util.ArrayList;
import java.util.List;

public class Contour {
    public List<EdgeHolder> edges = new ArrayList<>();

    private static void boundPoint(double[] bounds, Point2 p) {
        double xMin = bounds[0], yMin = bounds[1], xMax = bounds[2], yMax = bounds[3];
        if (p.x < xMin) xMin = p.x;
        if (p.y < yMin) yMin = p.y;
        if (p.x > xMax) xMax = p.x;
        if (p.y > yMax) yMax = p.y;
        bounds[0] = xMin;
        bounds[1] = yMin;
        bounds[2] = xMax;
        bounds[3] = yMax;
    }

    public void addEdge(EdgeHolder edge) {
        edges.add(edge);
    }

    public void addEdge(EdgeSegment segment) {
        edges.add(new EdgeHolder(segment));
    }

    public EdgeHolder addEdge() {
        var newEdge = new EdgeHolder();
        edges.add(newEdge);
        return edges.getLast();
    }

    public void bound(double[] bounds) {
        for (var edgeHolder : edges) {
            var edge = edgeHolder.get();
            if (edge != null) {
                edge.bound(bounds);
            }
        }
    }

    public void boundMiters(double[] bounds, double border, double miterLimit, int polarity) {
        if (edges.isEmpty()) return;

        var prevEdge = edges.getLast().get();
        for (var edgeHolder : edges) {
            var edge = edgeHolder.get();
            if (edge != null) {
                var prevDir = prevEdge.direction(1).normalize(false);
                var dir = edge.direction(0).negate().normalize(false);
                if (polarity * Vector2.crossProduct(prevDir, dir) >= 0) {
                    var miterLength = miterLimit;
                    var q = 0.5 * (1 - Vector2.dotProduct(prevDir, dir));
                    if (q > 0) {
                        miterLength = Math.min(1 / Math.sqrt(q), miterLimit);
                    }
                    var miter = new Point2(
                            edge.point(0).x + border * miterLength * prevDir.add(dir).normalize(false).x,
                            edge.point(0).y + border * miterLength * prevDir.add(dir).normalize(false).y
                    );
                    boundPoint(bounds, miter);
                }
                prevEdge = edge;
            }
        }
    }

    public int winding() {
        if (edges.isEmpty()) return 0;

        double total = 0;
        if (edges.size() == 1) {
            var edge = edges.getFirst().get();
            var a = edge.point(0);
            var b = edge.point(1.0 / 3.0);
            var c = edge.point(2.0 / 3.0);
            total += (b.x - a.x) * (a.y + b.y);
            total += (c.x - b.x) * (b.y + c.y);
            total += (a.x - c.x) * (c.y + a.y);
        } else if (edges.size() == 2) {
            var edge0 = edges.get(0).get();
            var edge1 = edges.get(1).get();
            var a = edge0.point(0);
            var b = edge0.point(0.5);
            var c = edge1.point(0);
            var d = edge1.point(0.5);
            total += (b.x - a.x) * (a.y + b.y);
            total += (c.x - b.x) * (b.y + c.y);
            total += (d.x - c.x) * (c.y + d.y);
            total += (a.x - d.x) * (d.y + a.y);
        } else {
            var prev = edges.getLast().get().point(0);
            for (var edgeHolder : edges) {
                var edge = edgeHolder.get();
                var cur = edge.point(0);
                total += (cur.x - prev.x) * (prev.y + cur.y);
                prev = cur;
            }
        }
        return Arithmetic.sign(total);
    }

    public void reverse() {
        var n = edges.size();
        for (var i = 0; i < n / 2; ++i) {
            EdgeHolder.swap(edges.get(i), edges.get(n - 1 - i));
        }
        for (var edgeHolder : edges) {
            var edge = edgeHolder.get();
            if (edge != null) {
                edge.reverse();
            }
        }
    }
}