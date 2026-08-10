package org.academy.internal.common.ability.meltdowner;

import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class ContinuousReflectionSession {
    private UUID reflectorId;
    private long nextChargeTick = Long.MIN_VALUE;

    private static long saturatingAdd(long value, int increment) {
        if (value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }

    public boolean activate(
            UUID candidateId,
            long currentTick,
            long firstDamagePulse,
            int damageInterval,
            BooleanSupplier payment
    ) {
        if (candidateId == null || damageInterval <= 0 || payment == null) return false;
        if (candidateId.equals(reflectorId) && currentTick < nextChargeTick) return true;
        if (!payment.getAsBoolean()) {
            clear();
            return false;
        }
        reflectorId = candidateId;
        nextChargeTick = saturatingAdd(firstDamagePulse, damageInterval);
        return true;
    }

    public boolean renewIfDue(
            UUID candidateId,
            long currentTick,
            int damageInterval,
            BooleanSupplier payment
    ) {
        if (!isActiveFor(candidateId)) return false;
        if (currentTick < nextChargeTick) return true;
        if (damageInterval <= 0 || payment == null || !payment.getAsBoolean()) {
            clear();
            return false;
        }
        nextChargeTick = saturatingAdd(currentTick, damageInterval);
        return true;
    }

    public boolean isActiveFor(UUID candidateId) {
        return candidateId != null && candidateId.equals(reflectorId);
    }

    UUID reflectorId() {
        return reflectorId;
    }

    public boolean isExpired(long currentTick) {
        return reflectorId != null && currentTick >= nextChargeTick;
    }

    public long nextChargeTick() {
        return nextChargeTick;
    }

    public void clear() {
        reflectorId = null;
        nextChargeTick = Long.MIN_VALUE;
    }
}
