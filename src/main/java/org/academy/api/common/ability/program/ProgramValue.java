package org.academy.api.common.ability.program;

import java.util.Objects;

/**
 * Runtime value paired with the same stable type used by graph ports.
 */
public record ProgramValue<T>(ProgramValueType type, T value) {
    public ProgramValue {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }
}
