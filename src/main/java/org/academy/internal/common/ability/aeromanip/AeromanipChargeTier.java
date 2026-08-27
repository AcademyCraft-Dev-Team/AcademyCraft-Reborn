package org.academy.internal.common.ability.aeromanip;

/** Shared release tiers for Aeromanipulation's tap/half/full charge gesture. */
public enum AeromanipChargeTier {
    INSTANT,
    HALF,
    FULL;

    public static final int HALF_CHARGE_TICKS = 8;
    public static final int FULL_CHARGE_TICKS = 24;

    public static AeromanipChargeTier fromTicks(long chargeTicks) {
        var ticks = Math.max(0L, chargeTicks);
        if (ticks >= FULL_CHARGE_TICKS) return FULL;
        if (ticks >= HALF_CHARGE_TICKS) return HALF;
        return INSTANT;
    }
}
