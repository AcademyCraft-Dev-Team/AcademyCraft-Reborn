package org.academy.internal.common.structure.sable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Runtime-only identity index; queries never mutate a backend constraint. */
public final class SableJointIndex<B, H> {
    private final Map<B, ArrayList<H>> handles = new IdentityHashMap<>();
    private final Predicate<H> valid;

    public SableJointIndex(Predicate<H> valid) {
        this.valid = valid;
    }

    public void record(B body, H handle) {
        if (body == null || handle == null) return;
        var entries = handles.computeIfAbsent(body, ignored -> new ArrayList<>());
        // Bound retention from repeated create/remove cycles even for bodies never queried by ACR.
        if (entries.size() >= 32) entries.removeIf(value -> !validOrUnknown(value));
        if (entries.stream().noneMatch(value -> value == handle)) entries.add(handle);
    }

    public boolean hasAny(B body) {
        var entries = handles.get(body);
        if (entries == null) return false;
        entries.removeIf(value -> !validOrUnknown(value));
        if (!entries.isEmpty()) return true;
        handles.remove(body);
        return false;
    }

    public void remove(B body) {
        handles.remove(body);
    }

    public void clear() {
        handles.clear();
    }

    private boolean validOrUnknown(H handle) {
        try {
            return valid.test(handle);
        } catch (RuntimeException | LinkageError unavailable) {
            return true;
        }
    }
}
