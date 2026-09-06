package org.academy.internal.server.ability;

/**
 * Pure input normalization for P.R.O.P.S activity rewards.
 */
final class PropsAcquisition {
    static final int JUMP_INTERVAL_TICKS = 4;

    private PropsAcquisition() {
    }

    static double damageReward(double healthDamage) {
        return finiteNonNegative(healthDamage) * 0.2;
    }

    static double healthLost(double healthBefore, double healthDamage) {
        return Math.min(finiteNonNegative(healthBefore), finiteNonNegative(healthDamage));
    }

    static int foodRestored(int foodBefore, int foodAfter) {
        return Math.max(0, foodAfter - foodBefore);
    }

    static boolean canRewardJump(int completedJumps, long gameTime, long lastRewardTick) {
        return completedJumps > 0 && (lastRewardTick == Long.MIN_VALUE
                || gameTime - lastRewardTick >= JUMP_INTERVAL_TICKS);
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
