package org.academy.internal.common.attribute;

import net.minecraft.util.Mth;

/**
 * Pure calculations shared by the P.R.O.P.S server and client.
 */
public final class PropsMath {
    public static final double MAX_TOTAL = 2_000.0;
    public static final double ACQUISITION_LOG_FACTOR = 0.1075;
    private static final double BASE_JUMP_STRENGTH = 0.42;
    private static final double BASE_SAFE_FALL_DISTANCE = 3.0;
    private static final double VERTICAL_DRAG = 0.98;
    private static final double GRAVITY_PER_TICK = 0.08;
    private static final int MAX_JUMP_SIMULATION_TICKS = 256;

    private PropsMath() {
    }

    public static double acquisitionCoefficient(double total) {
        if (total == Double.POSITIVE_INFINITY) return 0.0;
        var safeTotal = finiteNonNegative(total);
        if (safeTotal >= MAX_TOTAL) return 0.0;
        return Math.max(0.0, 1.0 - ACQUISITION_LOG_FACTOR * Math.log1p(safeTotal));
    }

    public static double awardedAmount(double total, double rawAmount, boolean bypassCoefficient) {
        var safeTotal = finiteNonNegative(total);
        var remaining = Math.max(0.0, MAX_TOTAL - safeTotal);
        if (remaining <= 0.0) return 0.0;
        var safeRaw = finiteNonNegative(rawAmount);
        if (safeRaw <= 0.0) return 0.0;
        var scaled = bypassCoefficient ? safeRaw : safeRaw * acquisitionCoefficient(safeTotal);
        return Math.min(remaining, scaled);
    }

    public static double muscleDamageBonus(double value) {
        return finiteNonNegative(value) * 0.05;
    }

    public static double enduranceHealthBonus(double value) {
        return finiteNonNegative(value) * 0.1;
    }

    public static double dexteritySpeedBonus(double value) {
        return finiteNonNegative(value) * 0.002;
    }

    public static double dexterityJumpStrengthBonus(double value) {
        return Mth.sqrt((float) (1.0 + finiteNonNegative(value) * 0.005)) - 1.0;
    }

    /**
     * Adds enough safe-fall distance to cover a jump powered only by the P.R.O.P.S dexterity
     * modifier. Rounding the simulated apex upward leaves room for landing collision rounding.
     */
    public static double dexteritySafeFallDistanceBonus(double value) {
        var jumpStrength = BASE_JUMP_STRENGTH * (1.0 + dexterityJumpStrengthBonus(value));
        var requiredSafeDistance = Math.ceil(jumpApexHeight(jumpStrength));
        return Math.max(0.0, requiredSafeDistance - BASE_SAFE_FALL_DISTANCE);
    }

    static double jumpApexHeight(double initialVelocity) {
        if (!Double.isFinite(initialVelocity) || initialVelocity <= 0.0) return 0.0;
        var velocity = initialVelocity;
        var height = 0.0;
        for (var tick = 0; tick < MAX_JUMP_SIMULATION_TICKS && velocity > 0.0; tick++) {
            height += velocity;
            velocity = (velocity - GRAVITY_PER_TICK) * VERTICAL_DRAG;
        }
        return height;
    }

    public static int perceptionEnchantmentBonus(double value) {
        return Mth.floor(finiteNonNegative(value) * 0.005);
    }

    public static double perceptionExperienceMultiplier(double value) {
        return 1.0 + finiteNonNegative(value) * 0.00005;
    }

    public static double neuralIterationMultiplier(double value) {
        return 1.0 + finiteNonNegative(value) * 0.0001;
    }

    public static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
