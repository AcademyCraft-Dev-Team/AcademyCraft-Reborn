package org.academy.internal.common.ability.program;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** One-call ordering only: keeps the original UUID-string order without formatting on every comparison. */
final class ProgramEntityTargetOrder {
    private ProgramEntityTargetOrder() {}

    static <T> List<T> sortedLimit(List<T> candidates, int maximumResults, Function<T, UUID> uuid) {
        if (maximumResults < 0) throw new IllegalArgumentException("Result limit cannot be negative");
        if (maximumResults == 0) return List.of();
        if (candidates.size() <= 1) return List.copyOf(candidates);
        return candidates.stream()
                .map(value -> new Keyed<>(value, uuid.apply(value).toString()))
                .sorted(Comparator.comparing(Keyed<T>::key))
                .limit(maximumResults)
                .map(Keyed::value)
                .toList();
    }

    private record Keyed<T>(T value, String key) {}
}
