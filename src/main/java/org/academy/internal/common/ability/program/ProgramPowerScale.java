package org.academy.internal.common.ability.program;

/**
 * Shared continuous power semantics for configurable ability-program actions.
 */
public final class ProgramPowerScale {
    /** Lowest selectable power. Legacy serialized zeroes are normalized to this value. */
    public static final float MIN = 0.01f;
    public static final float DEFAULT = 1.0f;
    public static final float MAX = 2.0f;
    public static final float MIN_COST_MULTIPLIER = 0.1f;
    public static final float MAX_COST_MULTIPLIER = 4.0f;
    private static final float LEGACY_MIN = 0.0f;
    private static final float DEFAULT_TANGENT = 1.0f;

    private ProgramPowerScale() {
    }

    public static float require(float power) {
        if (!isAccepted(power)) {
            throw new IllegalArgumentException("Program power must be between 0.01 and 2");
        }
        return Math.max(MIN, power);
    }

    /** Accepts legacy zero-valued programs while new editors expose 0.01 as the minimum. */
    public static boolean isAccepted(float power) {
        return Float.isFinite(power) && power >= LEGACY_MIN && power <= MAX;
    }

    /** Damage reaches the same endpoints with diminishing changes near either limit. */
    public static float damageMultiplier(float power) {
        return effectMultiplier(power);
    }

    /** Shared marginal curve for damage and non-damage effect strength. */
    public static float effectMultiplier(float power) {
        return marginalCurve(require(power), MIN, DEFAULT, MAX);
    }

    /** CP maps 0.01/1/2 power to 0.1/1/4 with diminishing changes at both limits. */
    public static float costMultiplier(float power) {
        return marginalCurve(
                require(power), MIN_COST_MULTIPLIER, DEFAULT, MAX_COST_MULTIPLIER);
    }

    public static float cost(float baseCost, float power) {
        return baseCost * costMultiplier(power);
    }

    /**
     * Continuously interpolates non-damage effects through the former three power tiers.
     */
    public static double interpolate(
            float power,
            double controlled,
            double standard,
            double maximum
    ) {
        var effect = effectMultiplier(power);
        return effect <= DEFAULT
                ? controlled + (standard - controlled)
                * ((effect - MIN) / (DEFAULT - MIN))
                : standard + (maximum - standard) * (effect - DEFAULT);
    }

    public static float interpolate(
            float power,
            float controlled,
            float standard,
            float maximum
    ) {
        return (float) interpolate(power, (double) controlled, standard, maximum);
    }

    private static float marginalCurve(
            float input,
            float minimumValue,
            float defaultValue,
            float maximumValue
    ) {
        if (input <= DEFAULT) {
            var span = DEFAULT - MIN;
            var progress = (input - MIN) / span;
            return cubicHermite(
                    progress,
                    minimumValue,
                    defaultValue,
                    0.0f,
                    DEFAULT_TANGENT * span
            );
        }
        var span = MAX - DEFAULT;
        var progress = (input - DEFAULT) / span;
        return cubicHermite(
                progress,
                defaultValue,
                maximumValue,
                DEFAULT_TANGENT * span,
                0.0f
        );
    }

    private static float cubicHermite(
            float progress,
            float start,
            float end,
            float startTangent,
            float endTangent
    ) {
        var squared = progress * progress;
        var cubed = squared * progress;
        return (2.0f * cubed - 3.0f * squared + 1.0f) * start
                + (cubed - 2.0f * squared + progress) * startTangent
                + (-2.0f * cubed + 3.0f * squared) * end
                + (cubed - squared) * endTangent;
    }
}
