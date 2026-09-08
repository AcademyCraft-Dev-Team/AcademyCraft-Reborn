package org.academy.internal.common.ability.mentalout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Continuous exposure is shared across effects and controllers, using server game ticks. */
final class MentalResistanceTracker {
    static final int EXPOSURE_LIMIT_TICKS = 20 * 20;
    static final int BLOCK_TICKS = 5 * 20;
    private final Map<UUID, Exposure> exposures = new HashMap<>();
    private final Map<UUID, Long> blockedUntil = new HashMap<>();

    void mark(UUID subjectId, long now) {
        if (remainingTicks(subjectId, now) > 0) return;
        var previous = exposures.get(subjectId);
        var start = previous == null || previous.lastTick < now - 1 || previous.lastTick > now
                ? now : previous.startTick;
        exposures.put(subjectId, new Exposure(start, now));
    }

    List<UUID> tick(long now, Predicate<UUID> eligible) {
        blockedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        var escaped = new ArrayList<UUID>();
        var iterator = exposures.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var exposure = entry.getValue();
            if (exposure.lastTick != now || !eligible.test(entry.getKey())) {
                iterator.remove();
            } else if (now - exposure.startTick > EXPOSURE_LIMIT_TICKS) {
                blockedUntil.merge(entry.getKey(), now + BLOCK_TICKS, Math::max);
                escaped.add(entry.getKey());
                iterator.remove();
            }
        }
        return escaped;
    }

    int remainingTicks(UUID subjectId, long now) {
        return (int) Math.clamp(blockedUntil.getOrDefault(subjectId, now) - now, 0, BLOCK_TICKS);
    }

    void remove(UUID subjectId) {
        exposures.remove(subjectId);
        blockedUntil.remove(subjectId);
    }

    void clear() {
        exposures.clear();
        blockedUntil.clear();
    }

    private record Exposure(long startTick, long lastTick) {
    }
}
