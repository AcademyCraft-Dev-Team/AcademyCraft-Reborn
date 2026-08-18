package org.academy.internal.coremod;

/**
 * Packs the protected health value together with the maximum health it was validated against.
 */
public final class ProtectedHealthCache {
    private static final float FULL_HEALTH_EPSILON = 1.0E-4f;

    private ProtectedHealthCache() {
    }

    public static long reconcile(
            long packed,
            boolean initialized,
            float observedHealth,
            float currentMaxHealth
    ) {
        var observed = finiteNonNegative(observedHealth, 0.0f);
        var hasCurrentMaximum = Float.isFinite(currentMaxHealth);
        var currentMaximum = hasCurrentMaximum
                ? Math.max(0.0f, currentMaxHealth)
                : Float.NaN;

        if (!initialized) {
            var initialMaximum = hasCurrentMaximum
                    ? currentMaximum
                    : Math.max(1.0f, observed);
            return pack(
                    hasCurrentMaximum ? Math.min(observed, currentMaximum) : observed,
                    initialMaximum
            );
        }

        var cached = health(packed);
        if (!Float.isFinite(cached)) cached = observed;
        else cached = Math.max(0.0f, cached);

        var previousMaximum = maxHealth(packed);
        if (!Float.isFinite(previousMaximum)) {
            previousMaximum = hasCurrentMaximum
                    ? currentMaximum
                    : Math.max(1.0f, cached);
        } else {
            previousMaximum = Math.max(0.0f, previousMaximum);
        }

        if (hasCurrentMaximum) {
            if (currentMaximum > previousMaximum && wasFull(cached, previousMaximum)) {
                cached = currentMaximum;
            }
            cached = Math.min(cached, currentMaximum);
            previousMaximum = currentMaximum;
            observed = Math.min(observed, currentMaximum);
        }

        if (observed > cached) cached = observed;
        return pack(cached, previousMaximum);
    }

    public static long subtract(long packed, float amount) {
        if (!Float.isFinite(amount) || !(amount > 0.0f)) return packed;
        return pack(Math.max(0.0f, health(packed) - amount), maxHealth(packed));
    }

    public static float health(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    public static float maxHealth(long packed) {
        return Float.intBitsToFloat((int) packed);
    }

    private static boolean wasFull(float health, float maximum) {
        if (!(maximum > 0.0f)) return false;
        var tolerance = Math.max(1.0f, maximum) * FULL_HEALTH_EPSILON;
        return health >= maximum - tolerance;
    }

    private static float finiteNonNegative(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : fallback;
    }

    private static long pack(float health, float maxHealth) {
        return (long) Float.floatToRawIntBits(health) << 32
                | Float.floatToRawIntBits(maxHealth) & 0xFFFF_FFFFL;
    }
}
