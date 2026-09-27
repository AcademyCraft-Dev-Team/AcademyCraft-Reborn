package org.academy.api.common.ability.program;

/**
 * Structural limits granted by a programmable skill host.
 */
public record ProgramLimits(int maxNodes, int maxEdges) {
    public static final ProgramLimits DEFAULT = new ProgramLimits(128, 256);

    public ProgramLimits {
        if (maxNodes <= 0 || maxEdges <= 0) {
            throw new IllegalArgumentException("Program limits must be positive");
        }
    }
}
