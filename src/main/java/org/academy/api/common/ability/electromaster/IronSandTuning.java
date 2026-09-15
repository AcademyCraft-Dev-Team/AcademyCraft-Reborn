package org.academy.api.common.ability.electromaster;

/** Pure values; MP discounts are independent of the CP proficiency profile. */
public final class IronSandTuning {
    public static final int HOLD_TICKS = 6;
    public static final int HIT_INTERVAL_TICKS = 10;
    public static final double INTERCEPTION_RADIUS = 2;
    public static final float DEFENSE_MAINTENANCE_CP = 40;

    private IronSandTuning() {}

    public static double massCostMultiplier(int milestone) { return milestone >= 1 ? 0.9 : 1; }
    public static double massCost(double base, int milestone) {
        return positive(base) * massCostMultiplier(milestone);
    }
    public static double capacity(double power, int milestone) {
        return 100 * positive(power) * (milestone >= 2 ? 1.2 : 1);
    }
    public static double recoveryPerTick(double power, boolean richBiome) {
        return positive(power) * (richBiome ? 1 : 0.5);
    }
    public static double whipRange(int milestone) { return milestone >= 2 ? 15 : 12; }
    public static double cloudRadius(int milestone) { return milestone >= 2 ? 20 : 16; }
    public static double whipBaseDamage(int milestone, boolean chargedBeforeHit) {
        return milestone >= 3 && chargedBeforeHit ? 15 : 10;
    }
    public static double scaleDamage(double base, double power, double multiplier) {
        var result = positive(base) * positive(power) * positive(multiplier);
        return Double.isFinite(result) ? Math.min(Float.MAX_VALUE, result) : 0;
    }
    private static double positive(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
}
