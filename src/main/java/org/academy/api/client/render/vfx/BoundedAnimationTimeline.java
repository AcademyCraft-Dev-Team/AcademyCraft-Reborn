package org.academy.api.client.render.vfx;

import org.jspecify.annotations.Nullable;

/** Local presentation time, independent of server/world ticks. One running and one newest pending event. */
public final class BoundedAnimationTimeline<T> {
    public record Frame<T>(T value, float progress) { }
    private record Event<T>(T value, double receivedAt, double duration, float initialProgress) { }
    private @Nullable Event<T> current;
    private @Nullable Event<T> pending;
    private double startedAt;
    private double continuationAt = Double.NaN;
    private long lastSequence;
    private long duplicates, coalesced, expired, started;

    public BoundedAnimationTimeline(long sequenceFloor) { lastSequence = sequenceFloor; }

    public boolean offer(long sequence, T value, double now, double duration, float initialProgress) {
        if (sequence <= lastSequence) { duplicates++; return false; }
        if (!Double.isFinite(now) || !Double.isFinite(duration) || duration <= 0
                || !Float.isFinite(initialProgress) || initialProgress < 0 || initialProgress >= 1) return false;
        lastSequence = sequence;
        if (pending != null) coalesced++;
        pending = new Event<>(value, now, duration, initialProgress);
        return true;
    }

    public void advance(double now) {
        if (current != null && now - startedAt + 1e-9 >= current.duration) {
            continuationAt = startedAt + current.duration;
            current = null;
        }
        if (pending != null && now - pending.receivedAt > 1.0) { pending = null; expired++; }
    }

    /** Call only when submitting a visible effect. Unrendered events cannot expire by remote tick age. */
    public @Nullable Frame<T> sample(double now) {
        advance(now);
        if (current == null && pending != null) {
            current = pending;
            pending = null;
            // Carry at most one normal frame across a continuous boundary. Starting every
            // queued stroke at "now" would accumulate rounding drift until valid events merge.
            boolean continuous = current.initialProgress == 0 && Double.isFinite(continuationAt)
                    && current.receivedAt <= continuationAt + 1e-9 && now - continuationAt <= 0.05;
            startedAt = continuous ? continuationAt : now - current.initialProgress * current.duration;
            continuationAt = Double.NaN;
            started++;
        }
        if (current == null) return null;
        return new Frame<>(current.value, (float) Math.clamp((now - startedAt) / current.duration, 0, 1));
    }

    public @Nullable T currentValue() { return current == null ? null : current.value; }
    public @Nullable T pendingValue() { return pending == null ? null : pending.value; }

    public boolean hasEvents() { return current != null || pending != null; }
    public long duplicates() { return duplicates; }
    public long coalesced() { return coalesced; }
    public long expired() { return expired; }
    public long started() { return started; }
}
