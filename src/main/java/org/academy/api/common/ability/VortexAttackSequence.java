package org.academy.api.common.ability;

import java.util.function.BooleanSupplier;

/** Tick-driven attack sequence; independent of CP recovery speed, rendering and entity type. */
public final class VortexAttackSequence {
    private VortexAttackPattern next = VortexAttackPattern.RISE_SLAM;
    private long availableTick = Long.MIN_VALUE;

    /** A rejected or unaffordable request neither pays a cost nor skips a pattern. */
    public VortexAttackPattern tryBegin(long tick, BooleanSupplier payCost) {
        if (tick < availableTick || !payCost.getAsBoolean()) return null;
        var accepted = next;
        availableTick = tick + accepted.durationTicks();
        next = accepted.next();
        return accepted;
    }
}
