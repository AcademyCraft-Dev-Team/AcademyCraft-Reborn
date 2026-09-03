package org.academy.api.common.ability.program;

import java.util.Objects;

/**
 * Server-authoritative spatial bounds for one ability-program category.
 *
 * <p>Query range limits world lookups and ray origins. Action range limits the targets and
 * positions that an action may affect. Individual nodes may impose a smaller skill-specific
 * range, but must never exceed these category bounds.</p>
 */
public record ProgramSpatialLimits(double queryRange, double actionRange) {
    public static final ProgramSpatialLimits DEFAULT = uniform(32.0);

    public ProgramSpatialLimits {
        requirePositiveFinite(queryRange, "Query range");
        requirePositiveFinite(actionRange, "Action range");
    }

    public static ProgramSpatialLimits uniform(double range) {
        return new ProgramSpatialLimits(range, range);
    }

    public double requireQueryRange(double range) {
        return requireWithin(range, queryRange, "Query range");
    }

    public double requireActionRange(double range) {
        return requireWithin(range, actionRange, "Action range");
    }

    public boolean containsQueryDistance(double distance) {
        return containsDistance(distance, queryRange);
    }

    public boolean containsActionDistance(double distance) {
        return containsDistance(distance, actionRange);
    }

    public double clampActionRange(double requestedRange) {
        if (!Double.isFinite(requestedRange) || requestedRange < 0.0) {
            throw new IllegalArgumentException("Action range must be finite and non-negative");
        }
        return Math.min(requestedRange, actionRange);
    }

    private static double requireWithin(double range, double maximum, String name) {
        if (!Double.isFinite(range) || range < 0.0 || range > maximum) {
            throw new IllegalArgumentException(name + " is outside the allowed limit");
        }
        return range;
    }

    private static boolean containsDistance(double distance, double maximum) {
        return Double.isFinite(distance) && distance >= 0.0 && distance <= maximum;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(Objects.requireNonNull(name) + " must be positive");
        }
    }
}
