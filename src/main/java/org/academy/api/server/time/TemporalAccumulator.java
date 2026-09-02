package org.academy.api.server.time;

/**
 * Converts a fractional simulation scale into a deterministic number of
 * logical ticks while retaining sub-tick credit.
 */
public final class TemporalAccumulator {
    private static final double MAX_FRACTIONAL_CARRY = Math.nextDown(1.0D);
    private double credit;

    public int advance(double scale, int maxLogicalTicks) {
        if (!Double.isFinite(scale) || scale < 0.0D) {
            throw new IllegalArgumentException("Temporal scale must be finite and non-negative.");
        }
        if (maxLogicalTicks < 1) {
            throw new IllegalArgumentException("maxLogicalTicks must be positive.");
        }
        if (scale == 0.0D) {
            return 0;
        }

        var available = Math.min(
                credit + scale,
                maxLogicalTicks + MAX_FRACTIONAL_CARRY
        );
        var ticks = Math.min((int) Math.floor(available), maxLogicalTicks);
        credit = Math.min(available - ticks, MAX_FRACTIONAL_CARRY);
        return ticks;
    }

    public double credit() {
        return credit;
    }

    public void reset() {
        credit = 0.0D;
    }
}
