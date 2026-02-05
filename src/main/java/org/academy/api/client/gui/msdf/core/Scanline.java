package org.academy.api.client.gui.msdf.core;

import java.util.ArrayList;
import java.util.List;

public class Scanline {
    private List<Intersection> intersections = new ArrayList<>();
    private int lastIndex;

    public Scanline() {
        lastIndex = 0;
    }

    public static boolean interpretFillRule(int intersections, FillRule fillRule) {
        return switch (fillRule) {
            case FILL_NONZERO -> intersections != 0;
            case FILL_ODD -> (intersections & 1) != 0;
            case FILL_POSITIVE -> intersections > 0;
            case FILL_NEGATIVE -> intersections < 0;
        };
    }

    public static double overlap(Scanline a, Scanline b, double xFrom, double xTo, FillRule fillRule) {
        double total = 0;
        boolean aInside = false, bInside = false;
        int ai = 0, bi = 0;
        var ax = !a.intersections.isEmpty() ? a.intersections.get(ai).x : xTo;
        var bx = !b.intersections.isEmpty() ? b.intersections.get(bi).x : xTo;

        while (ax < xFrom || bx < xFrom) {
            var xNext = Math.min(ax, bx);
            if (ax == xNext && ai < a.intersections.size()) {
                aInside = interpretFillRule(a.intersections.get(ai).direction, fillRule);
                ax = ++ai < a.intersections.size() ? a.intersections.get(ai).x : xTo;
            }
            if (bx == xNext && bi < b.intersections.size()) {
                bInside = interpretFillRule(b.intersections.get(bi).direction, fillRule);
                bx = ++bi < b.intersections.size() ? b.intersections.get(bi).x : xTo;
            }
        }

        var x = xFrom;
        while (ax < xTo || bx < xTo) {
            var xNext = Math.min(ax, bx);
            if (aInside == bInside) {
                total += xNext - x;
            }
            if (ax == xNext && ai < a.intersections.size()) {
                aInside = interpretFillRule(a.intersections.get(ai).direction, fillRule);
                ax = ++ai < a.intersections.size() ? a.intersections.get(ai).x : xTo;
            }
            if (bx == xNext && bi < b.intersections.size()) {
                bInside = interpretFillRule(b.intersections.get(bi).direction, fillRule);
                bx = ++bi < b.intersections.size() ? b.intersections.get(bi).x : xTo;
            }
            x = xNext;
        }

        if (aInside == bInside) {
            total += xTo - x;
        }
        return total;
    }

    private static int compareIntersections(Intersection a, Intersection b) {
        return Arithmetic.sign(a.x - b.x);
    }

    private void preprocess() {
        lastIndex = 0;
        if (!intersections.isEmpty()) {
            intersections.sort(Scanline::compareIntersections);
            var totalDirection = 0;
            for (var intersection : intersections) {
                totalDirection += intersection.direction;
                intersection.direction = totalDirection;
            }
        }
    }

    public void setIntersections(List<Intersection> intersections) {
        this.intersections = new ArrayList<>(intersections);
        preprocess();
    }

    private int moveTo(double x) {
        if (intersections.isEmpty()) return -1;
        var index = lastIndex;
        if (x < intersections.get(index).x) {
            do {
                if (index == 0) {
                    lastIndex = 0;
                    return -1;
                }
                --index;
            } while (x < intersections.get(index).x);
        } else {
            while (index < intersections.size() - 1 && x >= intersections.get(index + 1).x) {
                ++index;
            }
        }
        lastIndex = index;
        return index;
    }

    public int countIntersections(double x) {
        return moveTo(x) + 1;
    }

    public int sumIntersections(double x) {
        var index = moveTo(x);
        if (index >= 0) {
            return intersections.get(index).direction;
        }
        return 0;
    }

    public boolean filled(double x, FillRule fillRule) {
        return interpretFillRule(sumIntersections(x), fillRule);
    }

    public enum FillRule {
        FILL_NONZERO,
        FILL_ODD,
        FILL_POSITIVE,
        FILL_NEGATIVE
    }

    public static class Intersection {
        public double x;
        public int direction;

        public Intersection(double x, int direction) {
            this.x = x;
            this.direction = direction;
        }
    }
}