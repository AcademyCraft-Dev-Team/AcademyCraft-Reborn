package org.academy.internal.client.renderer.effect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

final class SweepAnimationTimeline<T> {
    static final int MAX_EVENTS_PER_ENTITY = 4;

    private final Map<Integer, ArrayDeque<Entry<T>>> entriesByEntity = new HashMap<>();

    void enqueue(int entityId, double startTick, T payload) {
        var entries = entriesByEntity.computeIfAbsent(entityId, ignored -> new ArrayDeque<>());
        while (entries.size() >= MAX_EVENTS_PER_ENTITY) entries.removeFirst();
        entries.addLast(new Entry<>(startTick, payload));
    }

    List<Entry<T>> entries(int entityId) {
        var entries = entriesByEntity.get(entityId);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    void prune(double currentTick, double durationTicks, IntPredicate entityExists) {
        entriesByEntity.entrySet().removeIf(byEntity -> {
            if (!entityExists.test(byEntity.getKey())) return true;
            byEntity.getValue().removeIf(entry -> progress(entry, currentTick, durationTicks) >= 1.0f);
            return byEntity.getValue().isEmpty();
        });
    }

    void clear() {
        entriesByEntity.clear();
    }

    int size(int entityId) {
        var entries = entriesByEntity.get(entityId);
        return entries == null ? 0 : entries.size();
    }

    static float progress(Entry<?> entry, double currentTick, double durationTicks) {
        if (durationTicks <= 0.0f) return 1.0f;
        return (float) ((currentTick - entry.startTick()) / durationTicks);
    }

    record Entry<T>(double startTick, T payload) {
    }
}
