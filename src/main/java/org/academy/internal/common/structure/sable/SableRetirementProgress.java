package org.academy.internal.common.structure.sable;

import org.academy.api.common.structure.StructureRef;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Volatile observations only. Durable deletion authority always remains in the journal. */
final class SableRetirementProgress {
    static final int RECENT_LIMIT = 16;
    record Attempt(StructureRef reference, SableRetirementStatus status, int attempts,
                   int repetitions, long lastTick, long nextTick) {}
    private final Map<StructureRef, Attempt> pending = new HashMap<>();
    private final ArrayDeque<Attempt> recent = new ArrayDeque<>();

    Attempt get(StructureRef reference) {
        return pending.getOrDefault(reference, new Attempt(reference, SableRetirementStatus.QUEUED, 0, 0, -1, 0));
    }

    boolean due(StructureRef reference, long tick) { return get(reference).nextTick() <= tick; }

    void record(StructureRef reference, SableRetirementStatus status, long tick, boolean remainsPending) {
        var previous = get(reference);
        int attempts = Math.min(Integer.MAX_VALUE - 1, previous.attempts()) + 1;
        int repetitions = previous.status() == status ? Math.min(32, previous.repetitions() + 1) : 1;
        long delay = Math.min(600, 20L << Math.min(5, repetitions - 1));
        var attempt = new Attempt(reference, status, attempts, repetitions, tick,
                remainsPending ? tick + delay : 0);
        if (remainsPending) pending.put(reference, attempt);
        else {
            pending.remove(reference);
            recent.addLast(attempt);
            while (recent.size() > RECENT_LIMIT) recent.removeFirst();
        }
    }

    List<Attempt> recent() { return List.copyOf(recent); }
}
