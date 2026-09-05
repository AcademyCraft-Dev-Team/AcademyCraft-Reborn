package org.academy.api.common.ability;

/** Shared visual sequence, independent of a player, renderer, or skill implementation. */
public enum VortexAttackPattern {
    RISE_SLAM(1), LEFT_WHIP(4), RIGHT_WHIP(5), COMPRESSED_THRUST(2), FOURFOLD_SLAM(3);

    private final int id;

    VortexAttackPattern(int id) {
        this.id = id;
    }

    public int id() { return id; }

    /** One complete windup, strike and recovery at twenty game ticks per second. */
    public int durationTicks() { return 30; }

    public float durationSeconds() { return durationTicks() / 20f; }

    public VortexAttackPattern next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static VortexAttackPattern byId(int id) {
        for (var pattern : values()) if (pattern.id == id) return pattern;
        return RISE_SLAM;
    }
}
