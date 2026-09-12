package org.academy.api.common.ability.program;

import java.util.Objects;

/**
 * Optional runtime input that overrides one encoded node-configuration field when connected.
 */
public record ProgramConfigurationPort(String field, ProgramValueType type) {
    public ProgramConfigurationPort {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Configuration port field cannot be blank");
        }
        Objects.requireNonNull(type, "type");
    }
}
