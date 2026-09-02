package org.academy.internal.server.time;

import org.academy.api.server.time.TemporalPauseSource;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

/** Reference-counted transient immunity contributions. */
final class TemporalImmunityState {
    private final Map<UUID, EnumMap<TemporalPauseSource, Integer>> counts =
            new HashMap<>();

    synchronized void acquire(UUID entityId, Set<TemporalPauseSource> sources) {
        var entityCounts = counts.computeIfAbsent(
                entityId,
                ignored -> new EnumMap<>(TemporalPauseSource.class)
        );
        for (var source : sources) {
            entityCounts.merge(source, 1, Integer::sum);
        }
    }

    synchronized void release(UUID entityId, Set<TemporalPauseSource> sources) {
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

    synchronized boolean isImmune(UUID entityId, TemporalPauseSource source) {
        var entityCounts = counts.get(entityId);
        return entityCounts != null && entityCounts.getOrDefault(source, 0) > 0;
    }

    synchronized boolean hasAny(UUID entityId) {
        var entityCounts = counts.get(entityId);
        return entityCounts != null && !entityCounts.isEmpty();
    }

    synchronized Set<TemporalPauseSource> sources(UUID entityId) {
        var entityCounts = counts.get(entityId);
        if (entityCounts == null || entityCounts.isEmpty()) return Set.of();
        return Set.copyOf(entityCounts.keySet());
    }

    synchronized Set<UUID> entityIds() {
        return Set.copyOf(new HashSet<>(counts.keySet()));
    }

    synchronized void clear() {
        counts.clear();
    }
}
