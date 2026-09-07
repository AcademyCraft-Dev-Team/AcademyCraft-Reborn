package org.academy.api.common.vfx;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded per-session revision and terminal-event guard, independent of rendering and entity IDs. */
public final class EffectSequence {
    private record Version(long revision, boolean terminal) {}
    private final Map<Long, Version> versions = new LinkedHashMap<>();
    private final int capacity;
    public EffectSequence(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Positive capacity required");
        this.capacity = capacity;
    }
    public boolean accept(long id, long revision, boolean terminal) {
        var previous = versions.get(id);
        if (previous != null && (previous.terminal || previous.revision >= revision)) return false;
        versions.put(id, new Version(revision, terminal));
        while (versions.size() > capacity) versions.remove(versions.keySet().iterator().next());
        return true;
    }
    public void clear() { versions.clear(); }
}
