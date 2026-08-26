package org.academy.internal.common.ability.program;

/**
 * Shared continuous power semantics for configurable ability-program actions.
 */
public final class ProgramPowerScale {
    public static final float MIN = 0.0f;
    public static final float DEFAULT = 1.0f;
    public static final float MAX = 2.0f;

    private ProgramPowerScale() {
    }

    public static float require(float power) {
        if (!Float.isFinite(power) || power < MIN || power > MAX) {
            throw new IllegalArgumentException("Program power must be between 0 and 2");
        }
        return power;
    }

    /**
     * Damage varies linearly from zero to twice the original skill damage.
     */
    public static float damageMultiplier(float power) {
        return require(power);
    }

    /**
     * CP follows the programmable-action cubic curve; power 1 preserves the original skill cost.
     */
    public static float cost(float baseCost, float power) {
        var checked = require(power);
        return baseCost * (0.5f + checked * checked * checked * 0.5f);
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
        var checked = require(power);
        return checked <= 1.0f
                ? controlled + (standard - controlled) * checked
                : standard + (maximum - standard) * (checked - 1.0f);
    }

    public static float interpolate(
            float power,
            float controlled,
            float standard,
            float maximum
    ) {
        return (float) interpolate(power, (double) controlled, standard, maximum);
    }
}
