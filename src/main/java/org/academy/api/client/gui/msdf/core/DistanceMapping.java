package org.academy.api.client.gui.msdf.core;

public class DistanceMapping {
    private final double scale;
    private final double translate;

    public DistanceMapping() {
        scale = 1;
        translate = 0;
    }

    public DistanceMapping(Range range) {
        var rangeWidth = range.upper - range.lower;
        scale = 1.0 / rangeWidth;
        translate = -range.lower;
    }

    private DistanceMapping(double scale, double translate) {
        this.scale = scale;
        this.translate = translate;
    }

    public static DistanceMapping inverse(Range range) {
        var rangeWidth = range.upper - range.lower;
        return new DistanceMapping(rangeWidth, range.lower / (rangeWidth != 0 ? rangeWidth : 1));
    }

    public double apply(double d) {
        return scale * (d + translate);
    }

    public double apply(Delta d) {
        return scale * d.value;
    }

    public DistanceMapping inverse() {
        return new DistanceMapping(1.0 / scale, -scale * translate);
    }

    public static class Delta {
        public double value;

        public Delta(double distanceDelta) {
            value = distanceDelta;
        }

        public double getValue() {
            return value;
        }
    }
}