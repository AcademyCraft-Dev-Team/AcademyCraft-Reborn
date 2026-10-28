package org.academy.api.common.ability.program;

import java.util.Objects;

/** A world-space displacement from each member's initial center, applied for a bounded force window. */
public record ProgramStructureMoveRequest(ProgramVector displacement, int durationTicks) {
    public static final double MAX_DISTANCE = 64.0;
    public static final int MAX_TICKS = 200;

    public ProgramStructureMoveRequest {
        Objects.requireNonNull(displacement, "displacement");
        if (!Double.isFinite(displacement.lengthSquared()) || displacement.lengthSquared() > MAX_DISTANCE * MAX_DISTANCE) {
            throw new IllegalArgumentException("Group displacement exceeds 64 blocks");
        }
        if (durationTicks < 2 || durationTicks > MAX_TICKS) {
            throw new IllegalArgumentException("Group movement duration must be in [2, 200] ticks");
        }
    }
}
