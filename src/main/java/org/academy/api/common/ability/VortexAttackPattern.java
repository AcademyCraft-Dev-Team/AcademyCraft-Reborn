package org.academy.api.common.ability;

/** Shared visual sequence, independent of a player, renderer, or skill implementation. */
public enum VortexAttackPattern {
    RISE_SLAM(1), COMPRESSED_THRUST(2), FOURFOLD_SLAM(3);

    private final int id;

    VortexAttackPattern(int id) {
        this.id = id;
    }

    public int id() { return id; }

    /** Match the existing ten-tick wing attack cadence. */
    public int durationTicks() { return 10; }

    public VortexAttackPattern next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static VortexAttackPattern byId(int id) {
        for (var pattern : values()) if (pattern.id == id) return pattern;
        return RISE_SLAM;
    }
}
