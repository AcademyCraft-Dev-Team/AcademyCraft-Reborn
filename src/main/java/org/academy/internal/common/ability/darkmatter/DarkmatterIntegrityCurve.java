package org.academy.internal.common.ability.darkmatter;

/**
 * Pure structural-integrity arithmetic, deliberately independent of item registries.
 */
public final class DarkmatterIntegrityCurve {
    private DarkmatterIntegrityCurve() {
    }

    public static float nextPassiveIntegrity(float current, int lifetimeTicks) {
        return nextPassiveIntegrity(current, 0.0f, lifetimeTicks).value();
    }

    public static Step nextPassiveIntegrity(float current, float remainder, int lifetimeTicks) {
        if (!Float.isFinite(current) || current <= 0.0f || lifetimeTicks <= 0) return Step.ZERO;
        var safeRemainder = Float.isFinite(remainder) ? remainder : 0.0f;
        var loss = 1.0 / lifetimeTicks;
        var exact = Math.max(0.0, (double) current + safeRemainder - loss);
        if (exact <= loss * 1.001) return Step.ZERO;
        var next = (float) exact;
        return new Step(next, (float) (exact - next));
    }

    public record Step(float value, float remainder) {
        public static final Step ZERO = new Step(0.0f, 0.0f);
    }
}
