package org.academy.internal.server.time;

/** Numeric rules for rebasing scheduled block and fluid ticks. */
final class TemporalScheduledTickMath {
    private TemporalScheduledTickMath() {
    }

    /**
     * Converts a channel scale into a multiplier relative to the level clock.
     * A stopped level clock is an upstream pause, so positive scheduled scales
     * retain their physical delay until that clock resumes.
     */
    static double relativeScale(double scheduledScale, double levelClockScale) {
        requireScale(scheduledScale, "scheduledScale");
        requireScale(levelClockScale, "levelClockScale");
        if (scheduledScale == 0.0D) return 0.0D;
        if (levelClockScale == 0.0D) return 1.0D;
        var relative = scheduledScale / levelClockScale;
        return Double.isFinite(relative) ? relative : Double.MAX_VALUE;
    }

    static double temporalRemaining(
            long now,
            long triggerTick,
            double oldRelativeScale,
            Double frozenRemaining
    ) {
        requireScale(oldRelativeScale, "oldRelativeScale");
        if (frozenRemaining != null) {
            if (!Double.isFinite(frozenRemaining) || frozenRemaining < 0.0D) {
                throw new IllegalArgumentException(
                        "Frozen scheduled-tick delay must be finite and non-negative."
                );
            }
            return frozenRemaining;
        }
        var physicalRemaining = Math.max(0L, triggerTick - now);
        if (oldRelativeScale == 0.0D) return physicalRemaining;
        var temporalRemaining = physicalRemaining * oldRelativeScale;
        return Double.isFinite(temporalRemaining)
                ? temporalRemaining : Double.MAX_VALUE;
    }

    static long rebasedTrigger(
            long now,
            double temporalRemaining,
            double newRelativeScale
    ) {
        if (!Double.isFinite(temporalRemaining) || temporalRemaining < 0.0D) {
            throw new IllegalArgumentException(
                    "Temporal remaining delay must be finite and non-negative."
            );
        }
        requireScale(newRelativeScale, "newRelativeScale");
        if (newRelativeScale == 0.0D) return safeAdd(now, 1L);
        var physicalDelay = Math.max(
                1L,
                ceilToLong(temporalRemaining / newRelativeScale)
        );
        return safeAdd(now, physicalDelay);
    }

    static int scaleNewDelay(int delay, double relativeScale) {
        requireScale(relativeScale, "relativeScale");
        if (delay <= 0) return delay;
        if (relativeScale == 0.0D) return 1;
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(1L, ceilToLong(delay / relativeScale))
        );
    }

    private static long ceilToLong(double value) {
        if (!Double.isFinite(value) || value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.ceil(Math.max(0.0D, value));
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static void requireScale(double scale, String name) {
        if (!Double.isFinite(scale) || scale < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
