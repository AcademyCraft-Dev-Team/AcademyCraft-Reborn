package org.academy.internal.common.sublevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Runtime activation identities for SubLevel-backed StructureHandles. */
public final class SubLevelActivationRegistry {
    private static final Map<SubLevelId, Activation> ACTIVATIONS = new HashMap<>();

    private SubLevelActivationRegistry() {
    }

    /** Returns the current activation, creating generation 1 on first use. */
    public static synchronized Activation activation(SubLevelId id) {
        Objects.requireNonNull(id, "id");
        return ACTIVATIONS.computeIfAbsent(id, ignored -> new Activation(UUID.randomUUID(), 1L));
    }

    /** Creates a fresh runtime identity and advances the persistent generation. */
    public static synchronized Activation advance(SubLevelId id) {
        Objects.requireNonNull(id, "id");
        var previous = ACTIVATIONS.get(id);
        var next = new Activation(UUID.randomUUID(), previous == null ? 1L : previous.generation() + 1L);
        ACTIVATIONS.put(id, next);
        return next;
    }

    public static synchronized void invalidate(SubLevelId id) {
        ACTIVATIONS.remove(Objects.requireNonNull(id, "id"));
    }

    public static synchronized Optional<Activation> find(SubLevelId id) {
        return Optional.ofNullable(ACTIVATIONS.get(id));
    }

    public static synchronized boolean matches(SubLevelId id, UUID runtimeId, long generation) {
        var activation = ACTIVATIONS.get(id);
        return activation != null && activation.runtimeId().equals(runtimeId)
                && activation.generation() == generation;
    }

    public static synchronized void clear() {
        ACTIVATIONS.clear();
    }

    public record Activation(UUID runtimeId, long generation) {
        public Activation {
            Objects.requireNonNull(runtimeId, "runtimeId");
            if (generation < 1) throw new IllegalArgumentException("generation must be positive");
        }
    }
}
