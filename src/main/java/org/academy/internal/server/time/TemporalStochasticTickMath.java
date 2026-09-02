package org.academy.internal.server.time;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/** Statistical scaling rules for one stochastic simulation callback. */
final class TemporalStochasticTickMath {
    private static final double INTEGER_EPSILON = 1.0E-12D;

    private TemporalStochasticTickMath() {
    }

    static int invocationCount(
            double scale,
            DoubleSupplier fractionalSample
    ) {
        if (!Double.isFinite(scale) || scale < 0.0D) {
            throw new IllegalArgumentException(
                    "Stochastic tick scale must be finite and non-negative."
            );
        }
        if (scale == 0.0D) return 0;

        var nearestInteger = Math.rint(scale);
        if (Math.abs(scale - nearestInteger) <= INTEGER_EPSILON) {
            return (int) nearestInteger;
        }
        var wholeInvocations = (int) Math.floor(scale);
        var fraction = scale - wholeInvocations;
        if (fraction == 0.0D) return wholeInvocations;

        Objects.requireNonNull(fractionalSample, "fractionalSample");
        var sample = fractionalSample.getAsDouble();
        if (!Double.isFinite(sample) || sample < 0.0D || sample >= 1.0D) {
            throw new IllegalArgumentException(
                    "Stochastic fractional sample must be in [0, 1)."
            );
        }
        return wholeInvocations + (sample < fraction ? 1 : 0);
    }
}
