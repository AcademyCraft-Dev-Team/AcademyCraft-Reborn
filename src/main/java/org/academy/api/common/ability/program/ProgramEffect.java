package org.academy.api.common.ability.program;

import java.util.Objects;

/** Cleanup for a reversible effect. A completed one-shot effect has no lifetime or undo operation. */
public record ProgramEffect(int lifetimeTicks, AutoCloseable cleanup) {
    public ProgramEffect {
        if (lifetimeTicks < 0 || lifetimeTicks > 72000) throw new IllegalArgumentException("Effect lifetime outside 0..72000");
        Objects.requireNonNull(cleanup);
    }
    public static ProgramEffect completed() { return new ProgramEffect(0, () -> {}); }
    public static ProgramEffect lasting(int ticks, AutoCloseable cleanup) {
        if (ticks < 1) throw new IllegalArgumentException("Lasting effect needs a positive lifetime");
        return new ProgramEffect(ticks, cleanup);
    }
}
