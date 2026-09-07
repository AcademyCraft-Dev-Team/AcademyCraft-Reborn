package org.academy.api.common.vfx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Bounded latest-state queue; transitions/end events bypass it and cancel older pending updates. */
public final class EffectUpdateQueue<T> {
    private record Pending<T>(T value, long tick) {}
    private final Map<Long, Pending<T>> pending = new LinkedHashMap<>();
    private final int capacity;
    private long dropped;
    public EffectUpdateQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Positive capacity required");
        this.capacity = capacity;
    }
    public void offer(long id, T state, long tick) {
        if (!pending.containsKey(id) && pending.size() >= capacity) {
            pending.remove(pending.keySet().iterator().next());
            dropped++;
        }
        pending.put(id, new Pending<>(state, tick));
    }
    public void cancel(long id) { pending.remove(id); }
    public int size() { return pending.size(); }
    public long dropped() { return dropped; }
    public void drain(long tick, long maxAge, int limit, Consumer<T> sink) {
        var iterator = pending.values().iterator();
        int sent = 0;
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (tick - next.tick > maxAge) { iterator.remove(); dropped++; continue; }
            if (sent >= limit) break;
            iterator.remove();
            sink.accept(next.value);
            sent++;
        }
    }
}
