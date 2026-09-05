package org.academy.internal.common.ability.aeromanip;

/** Client presentation state; predicted progress never grants a release tier. */
public final class AeromanipChargeProgress {
    private long gesture;
    private int confirmedTier = -1;

    public void begin(long gesture) {
        this.gesture = gesture;
        confirmedTier = -1;
    }

    public boolean accept(long gesture, int tier) {
        if (!matches(gesture) || tier < 0 || tier >= AeromanipChargeTier.values().length) return false;
        confirmedTier = Math.max(confirmedTier, tier);
        return true;
    }

    public boolean matches(long gesture) {
        return this.gesture == gesture;
    }

    public AeromanipChargeTier tier() {
        return AeromanipChargeTier.values()[Math.max(0, confirmedTier)];
    }

    public boolean awaitingConfirmation(long predictedTicks) {
        return confirmedTier < AeromanipChargeTier.fromTicks(predictedTicks).ordinal();
    }
}
