package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramValue;
import org.academy.api.common.ability.program.ProgramValueType;
import org.academy.api.common.ability.program.ProgramValueTypes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ProgramInputView {
    private final Map<String, List<ProgramValue<?>>> values;

    ProgramInputView(Map<String, List<ProgramValue<?>>> values) {
        this.values = values.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())
        ));
    }

    public List<ProgramValue<?>> all(String port) {
        return values.getOrDefault(port, List.of());
    }

    public Optional<ProgramValue<?>> first(String port) {
        var candidates = all(port);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.getFirst());
    }

    public ProgramValue<?> require(String port, ProgramValueType type) {
        var value = first(port).orElseThrow(() ->
                new IllegalArgumentException("Missing program input " + port));
        if (!value.type().equals(type)) {
            throw new IllegalArgumentException("Unexpected program input type for " + port);
        }
        return value;
    }

    public ProgramValue<?> requireCompatible(String port, ProgramValueType targetType) {
        var value = first(port).orElseThrow(() ->
                new IllegalArgumentException("Missing program input " + port));
        if (!ProgramValueTypes.canConnect(value.type(), targetType)) {
            throw new IllegalArgumentException("Incompatible program input type for " + port);
        }
        return value;
    }
}
