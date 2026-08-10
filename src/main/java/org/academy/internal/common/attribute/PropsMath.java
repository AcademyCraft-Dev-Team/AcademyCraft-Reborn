package org.academy.internal.common.attribute;

/**
 * Pure calculations shared by the P.R.O.P.S server and client.
 */
public final class PropsMath {
    public static final double MAX_TOTAL = 2_000.0;
    public static final double ACQUISITION_LOG_FACTOR = 0.1075;

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
        return Math.sqrt(1.0 + finiteNonNegative(value) * 0.005) - 1.0;
    }

    public static int perceptionEnchantmentBonus(double value) {
        return (int) Math.floor(finiteNonNegative(value) * 0.005);
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
