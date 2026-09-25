package org.academy.internal.server.ability;

/**
 * Pure input normalization for P.R.O.P.S activity rewards.
 */
final class PropsAcquisition {
    static final int JUMP_INTERVAL_TICKS = 4;
    static final int EXPERIENCE_PER_PERCEPTION_REWARD = 10;
    static final double PERCEPTION_PER_EXPERIENCE_BATCH = 0.1;

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

    static ExperienceProgress experienceProgress(int remainder, int pickedUpExperience) {
        var normalizedRemainder = Math.clamp(
                remainder, 0, EXPERIENCE_PER_PERCEPTION_REWARD - 1);
        var total = normalizedRemainder + (long) Math.max(0, pickedUpExperience);
        return new ExperienceProgress(
                (int) (total / EXPERIENCE_PER_PERCEPTION_REWARD),
                (int) (total % EXPERIENCE_PER_PERCEPTION_REWARD)
        );
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    record DistanceProgress(int blocks, int remainingCentimeters) {
    }

    record ExperienceProgress(int rewardBatches, int remainingExperience) {
        double perceptionReward() {
            return rewardBatches * PERCEPTION_PER_EXPERIENCE_BATCH;
        }
    }
}
