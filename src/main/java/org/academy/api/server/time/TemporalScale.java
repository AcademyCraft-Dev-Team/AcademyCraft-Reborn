package org.academy.api.server.time;

/** Shared scale composition rules for time domains. */
public final class TemporalScale {
    public static final double DEFAULT_MAX_SCALE = 8.0D;

    private TemporalScale() {
    }

    /**
     * Multiplies all applicable scales. A zero scale is a hard pause unless
     * the subject is immune to that pause source.
     */
    public static double compose(
            Iterable<Double> scales,
            boolean pauseImmune,
            double maxScale
    ) {
        if (!Double.isFinite(maxScale) || maxScale < 1.0D) {
            throw new IllegalArgumentException("maxScale must be finite and at least one.");
        }

        var logarithmicScale = 0.0D;
        for (var boxedScale : scales) {
            if (boxedScale == null) {
                throw new IllegalArgumentException("Temporal scale cannot be null.");
            }
            var scale = boxedScale.doubleValue();
            if (!Double.isFinite(scale) || scale < 0.0D) {
                throw new IllegalArgumentException("Temporal scale must be finite and non-negative.");
            }
            if (scale == 0.0D) {
                if (!pauseImmune) return 0.0D;
                continue;
            }
            logarithmicScale += Math.log(scale);
        }
        if (logarithmicScale >= Math.log(maxScale)) return maxScale;
        return Math.exp(logarithmicScale);
    }
}
