package org.academy.internal.server.misaka;

import java.util.Locale;

/**
 * Network-pool compute sinks. Allocations are percentages of leftover MSk after personal share.
 * Only {@link #CP} applies an effect this release; others are placeholders that still count toward 100%.
 */
public enum MisakaComputeSink {
    CP(true),
    ITERATION(false),
    DAMAGE(false),
    DISASSEMBLE(false);

    public static final int COUNT = values().length;

    private final boolean appliesEffect;

    MisakaComputeSink(boolean appliesEffect) {
        this.appliesEffect = appliesEffect;
    }

    public int id() {
        return ordinal();
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean appliesEffect() {
        return appliesEffect;
    }

    public static MisakaComputeSink byId(int id) {
        var values = values();
        if (id < 0 || id >= values.length) {
            return null;
        }
        return values[id];
    }

    /** Clamp each percent to 0..100 and truncate so the sum never exceeds 100 (last-written wins cut). */
    public static int[] clampAllocations(int[] raw) {
        var result = new int[COUNT];
        if (raw == null) {
            return result;
        }
        int sum = 0;
        for (int i = 0; i < COUNT; i++) {
            int value = i < raw.length ? Math.max(0, Math.min(100, raw[i])) : 0;
            if (sum + value > 100) {
                value = 100 - sum;
            }
            result[i] = value;
            sum += value;
        }
        return result;
    }

    public static int sum(int[] percents) {
        if (percents == null) {
            return 0;
        }
        int sum = 0;
        for (int i = 0; i < Math.min(COUNT, percents.length); i++) {
            sum += Math.max(0, percents[i]);
        }
        return sum;
    }
}
