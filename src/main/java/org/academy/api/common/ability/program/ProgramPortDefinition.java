package org.academy.api.common.ability.program;

import java.util.Objects;

/**
 * A stable named input or output on a program node.
 */
public record ProgramPortDefinition(
        String name,
        ProgramValueType type,
        boolean required,
        int maxConnections
) {
    public static final int UNBOUNDED_CONNECTIONS = Integer.MAX_VALUE;

    public ProgramPortDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) throw new IllegalArgumentException("Program port name cannot be blank");
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Program port connection limit must be positive");
        }
    }

    public static ProgramPortDefinition requiredInput(String name, ProgramValueType type) {
        return new ProgramPortDefinition(name, type, true, 1);
    }

    public static ProgramPortDefinition optionalInput(String name, ProgramValueType type) {
        return new ProgramPortDefinition(name, type, false, 1);
    }

    public static ProgramPortDefinition output(String name, ProgramValueType type) {
        return new ProgramPortDefinition(name, type, false, UNBOUNDED_CONNECTIONS);
    }
}
