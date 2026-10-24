package org.academy.api.common.ability.program;

import java.util.Objects;

/**
 * Requested absolute target rotation of a controlled structure: a signed axis and an angle
 * in radians. Backends that cannot apply an attitude request report it as unavailable.
 */
public record ProgramStructurePose(ProgramDirection axis, double angleRadians) {
    public ProgramStructurePose {
        Objects.requireNonNull(axis, "axis");
        if (!Double.isFinite(angleRadians)) {
            throw new IllegalArgumentException("Structure pose angle is not finite");
        }
    }
}
