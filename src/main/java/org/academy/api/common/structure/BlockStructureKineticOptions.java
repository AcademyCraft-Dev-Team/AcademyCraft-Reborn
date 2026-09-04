package org.academy.api.common.structure;

/** Bounded settings shared by ability and non-player structure controllers. */
public record BlockStructureKineticOptions(
        int durationTicks,
        double maximumSpeed,
        int impactCooldownTicks,
        boolean gravityAfter,
        boolean preventSettlementWhileActive
) {
    public static final int MAXIMUM_DURATION_TICKS = 72_000;

    public BlockStructureKineticOptions {
        if (durationTicks < 1 || durationTicks > MAXIMUM_DURATION_TICKS) {
            throw new IllegalArgumentException("durationTicks must be between 1 and "
                    + MAXIMUM_DURATION_TICKS);
        }
        if (!Double.isFinite(maximumSpeed) || maximumSpeed <= 0.0 || maximumSpeed > 16.0) {
            throw new IllegalArgumentException("maximumSpeed must be between 0 and 16");
        }
        if (impactCooldownTicks < 0 || impactCooldownTicks > 1_200) {
            throw new IllegalArgumentException(
                    "impactCooldownTicks must be between 0 and 1200");
        }
    }

    public BlockStructureKineticOptions(
            int durationTicks,
            double maximumSpeed,
            int impactCooldownTicks,
            boolean gravityAfter
    ) {
        this(durationTicks, maximumSpeed, impactCooldownTicks, gravityAfter, false);
    }
}
