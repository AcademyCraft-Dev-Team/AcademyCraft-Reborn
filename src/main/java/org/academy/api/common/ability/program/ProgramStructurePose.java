package org.academy.api.common.ability.program;

import java.util.Objects;

/**
 * Requested target rotation of a controlled structure: an axis and an angle in radians.
 * Finite-torque attitude control is not implemented yet, so rotation nodes report this input
 * back as unavailable instead of assigning the pose unconditionally.
 */
public record ProgramStructurePose(ProgramDirection axis, double angleRadians) {
    public ProgramStructurePose {
        Objects.requireNonNull(axis, "axis");
        if (!Double.isFinite(angleRadians)) {
            throw new IllegalArgumentException("Structure pose angle is not finite");
        }
    }
}
