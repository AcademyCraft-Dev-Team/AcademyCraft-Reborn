package org.academy.internal.server.ability;

/** Pure input normalization for P.R.O.P.S activity rewards. */
final class PropsAcquisition {
    private static final double MAX_MELEE_DAMAGE_PER_HIT = 10.0;

    private PropsAcquisition() {
    }

    static double meleeDamage(double healthDamage) {
        return Math.min(MAX_MELEE_DAMAGE_PER_HIT, finiteNonNegative(healthDamage));
    }

    static double healthLost(double healthBefore, double healthDamage) {
        return Math.min(finiteNonNegative(healthBefore), finiteNonNegative(healthDamage));
    }

    static int foodRestored(int foodBefore, int foodAfter) {
        return Math.max(0, foodAfter - foodBefore);
    }

    static int experienceGained(int amount) {
        return Math.max(0, amount);
    }

    static int statIncrease(int current, int previous) {
        return current >= previous ? current - previous : 0;
    }

    static DistanceProgress distanceProgress(int remainder, int currentStat, int previousStat) {
        var centimeters = Math.max(0, remainder) + (long) statIncrease(currentStat, previousStat);
        return new DistanceProgress((int) (centimeters / 100), (int) (centimeters % 100));
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    record DistanceProgress(int blocks, int remainingCentimeters) {
    }
}
