package org.academy.api.common.entitycontrol;

import java.util.Arrays;
import java.util.function.LongSupplier;

/** Server-thread cooperative budget. An individual world callback cannot be interrupted. */
public final class WorkBudget {
    public enum Operation {
        WORKER(128), BLOCK(512), ENTITY(256), CLAIM(1024), PATH(8), SLOT(256), TRANSFER(64), ACTION(64), RESTORE(8);
        private final int limit;
        Operation(int limit) { this.limit = limit; }
        public int limit() { return limit; }
    }

    public record Metrics(long tick, long nanos, long longestOperationNanos, int[] used, int[] deferred) {
        public Metrics { used = used.clone(); deferred = deferred.clone(); }
        @Override public int[] used() { return used.clone(); }
        @Override public int[] deferred() { return deferred.clone(); }
    }

    private final LongSupplier clock;
    private final long limitNanos;
    private final int[] used = new int[Operation.values().length];
    private final int[] deferred = new int[Operation.values().length];
    private long tick = Long.MIN_VALUE, nanos, longest, started;
    private int depth;
    private Metrics previous = new Metrics(Long.MIN_VALUE, 0, 0, used, deferred);

    public WorkBudget() { this(System::nanoTime, 2_000_000); }
    public WorkBudget(LongSupplier clock, long limitNanos) {
        if (limitNanos <= 0) throw new IllegalArgumentException("Positive time budget required");
        this.clock = clock;
        this.limitNanos = limitNanos;
    }

    public void beginTick(long value) {
        if (tick == value) return;
        if (depth != 0) throw new IllegalStateException("Unclosed work measurement");
        previous = metrics();
        tick = value;
        nanos = longest = 0;
        Arrays.fill(used, 0);
        Arrays.fill(deferred, 0);
    }

    public boolean hasTime() { return nanos + (depth == 0 ? 0 : clock.getAsLong() - started) < limitNanos; }
    public boolean available(Operation operation) { return hasTime() && used[operation.ordinal()] < operation.limit; }
    public boolean spend(Operation operation) {
        return reserve(operation, 1);
    }
    public boolean reserve(Operation operation, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative reservation");
        if (!hasTime() || count > operation.limit - used[operation.ordinal()]) { deferred[operation.ordinal()]++; return false; }
        used[operation.ordinal()] += count;
        return true;
    }

    /** Nested scopes count elapsed time once while retaining the longest individual scope. */
    public Measurement measure() {
        long now = clock.getAsLong();
        if (depth++ == 0) started = now;
        return new Measurement(now);
    }

    public Metrics metrics() { return new Metrics(tick, nanos, longest, used, deferred); }
    public Metrics previousMetrics() { return previous; }

    public final class Measurement implements AutoCloseable {
        private final long start;
        private boolean closed;
        private Measurement(long start) { this.start = start; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            long now = clock.getAsLong();
            longest = Math.max(longest, now - start);
            if (--depth == 0) nanos += now - started;
        }
    }
}
