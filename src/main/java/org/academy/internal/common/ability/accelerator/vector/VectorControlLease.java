package org.academy.internal.common.ability.accelerator.vector;

import org.academy.api.server.ability.AbilityExecutionHandle;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** One server-owned invocation. Duplicate program/server ticks never advance its delegate twice. */
final class VectorControlLease implements AbilityExecutionHandle {
    private final long deadline;
    private final LongSupplier clock;
    private final BooleanSupplier admitted;
    private final AbilityExecutionHandle delegate;
    private final Runnable released;
    private long lastTick = Long.MIN_VALUE;
    private boolean stopped;
    private String termination = "";

    VectorControlLease(long now, int duration, LongSupplier clock, BooleanSupplier admitted,
                       AbilityExecutionHandle delegate, Runnable released) {
        if (duration < 1 || duration > 1200) throw new IllegalArgumentException("Invalid lease duration");
        this.deadline = Math.addExact(now, duration);
        this.clock = Objects.requireNonNull(clock);
        this.admitted = Objects.requireNonNull(admitted);
        this.delegate = Objects.requireNonNull(delegate);
        this.released = Objects.requireNonNull(released);
    }

    @Override
    public boolean tick() {
        long now = clock.getAsLong();
        if (stopped) return false;
        // Recheck admission even when another caller already ticked this lease in this frame.
        try {
            if (now >= deadline || !admitted.getAsBoolean()) {
                termination = now >= deadline ? "expired" : "admission_lost";
                stop();
                return false;
            }
            if (lastTick == now) return true;
            lastTick = now;
            if (!delegate.tick()) {
                termination = "provider_completed";
                stop();
                return false;
            }
            return true;
        } catch (RuntimeException failure) {
            termination = "provider_failed:" + failure.getClass().getSimpleName();
            stop();
            return false;
        }
    }

    @Override
    public void stop() {
        clock.getAsLong(); // Also enforces the runtime's server-thread contract.
        if (stopped) return;
        stopped = true;
        if (termination.isEmpty()) termination = "canceled";
        try {
            delegate.stop();
        } catch (RuntimeException failure) {
            termination += ":cleanup_failed:" + failure.getClass().getSimpleName();
        } finally {
            released.run();
        }
    }

    boolean stopped() { return stopped; }
    String termination() { return termination; }
}
