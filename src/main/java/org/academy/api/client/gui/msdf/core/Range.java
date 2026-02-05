package org.academy.api.client.gui.msdf.core;

public class Range {
    public double lower, upper;

    public Range() {
        lower = 0;
        upper = 0;
    }

    public Range(double symmetricalWidth) {
        lower = -0.5 * symmetricalWidth;
        upper = 0.5 * symmetricalWidth;
    }

    public Range(double lowerBound, double upperBound) {
        lower = lowerBound;
        upper = upperBound;
    }

    public static Range multiply(double factor, Range range) {
        return new Range(factor * range.lower, factor * range.upper);
    }

    public Range multiply(double factor) {
        lower *= factor;
        upper *= factor;
        return this;
    }

    public Range divide(double divisor) {
        lower /= divisor;
        upper /= divisor;
        return this;
    }

    public Range multiplied(double factor) {
        return new Range(lower * factor, upper * factor);
    }

    public Range divided(double divisor) {
        return new Range(lower / divisor, upper / divisor);
    }
}