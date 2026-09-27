package org.academy.internal.server.time;

import org.academy.api.server.time.TemporalPauseSource;

import java.util.*;

/**
 * Reference-counted transient immunity contributions.
 */
final class TemporalImmunityState {
    private final Map<UUID, EnumMap<TemporalPauseSource, Integer>> counts =
            new HashMap<>();

    void acquire(UUID entityId, Set<TemporalPauseSource> sources) {
        var entityCounts = counts.computeIfAbsent(
                entityId,
                ignored -> new EnumMap<>(TemporalPauseSource.class)
        );
        for (var source : sources) {
            entityCounts.merge(source, 1, Integer::sum);
        }
    }

    void release(UUID entityId, Set<TemporalPauseSource> sources) {
        var entityCounts = counts.get(entityId);
        if (entityCounts == null) return;

        for (var source : sources) {
            var count = entityCounts.get(source);
            if (count == null) continue;
            if (count <= 1) entityCounts.remove(source);
            else entityCounts.put(source, count - 1);
        }
        if (entityCounts.isEmpty()) counts.remove(entityId);
    }

    boolean isImmune(UUID entityId, TemporalPauseSource source) {
        var entityCounts = counts.get(entityId);
        return entityCounts != null && entityCounts.getOrDefault(source, 0) > 0;
    }

    boolean hasAny(UUID entityId) {
        var entityCounts = counts.get(entityId);
        return entityCounts != null && !entityCounts.isEmpty();
    }

    Set<TemporalPauseSource> sources(UUID entityId) {
        var entityCounts = counts.get(entityId);
        if (entityCounts == null || entityCounts.isEmpty()) return Set.of();
        return Set.copyOf(entityCounts.keySet());
    }

    Set<UUID> entityIds() {
        return Set.copyOf(new HashSet<>(counts.keySet()));
    }

    void clear() {
        counts.clear();
    }
}
