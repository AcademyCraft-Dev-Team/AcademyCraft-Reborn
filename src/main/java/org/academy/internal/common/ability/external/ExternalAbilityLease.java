package org.academy.internal.common.ability.external;

import org.academy.api.server.ability.AbilityExecutionHandle;
import org.academy.api.server.ability.external.ExternalAbilityExecution;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Time-based lease with observable terminal failures and exactly-once cleanup. */
final class ExternalAbilityLease implements ExternalAbilityExecution {
    private final LongSupplier clock;
    private final Runnable requireThread;
    private final Supplier<String> admission;
    private final AbilityExecutionHandle delegate;
    private final long startedAt;
    private final int duration;
    private State state = State.RUNNING;
    private String detail = "";
    private long lastTick = Long.MIN_VALUE;

    ExternalAbilityLease(LongSupplier clock, Runnable requireThread, Supplier<String> admission,
                         AbilityExecutionHandle delegate, int duration) {
        this.clock = Objects.requireNonNull(clock); this.requireThread = Objects.requireNonNull(requireThread);
        this.admission = Objects.requireNonNull(admission); this.delegate = Objects.requireNonNull(delegate);
        if (duration < 1 || duration > 1200) throw new IllegalArgumentException("Invalid ability lease duration");
        this.duration = duration; this.startedAt = clock.getAsLong();
    }

    @Override public State state() { return state; }
    @Override public String detail() { return detail; }
    @Override public boolean tick() {
        requireThread.run();
        if (state != State.RUNNING) return false;
        try {
            var reason = Objects.requireNonNull(admission.get(), "Missing admission result");
            if (!reason.isEmpty()) return finish(State.FAILED, reason);
            long now = clock.getAsLong();
            if (now < startedAt || now - startedAt >= duration) return finish(State.COMPLETED, "Duration elapsed");
            if (now == lastTick) return true;
            lastTick = now;
            return delegate.tick() || finish(State.COMPLETED, "");
        } catch (RuntimeException | LinkageError failure) {
            return finish(State.FAILED, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }
    @Override public void stop() {
        requireThread.run();
        if (state == State.RUNNING) finish(State.CANCELLED, "Cancelled");
    }
    private boolean finish(State next, String reason) {
        state = next; detail = reason;
        try { delegate.stop(); }
        catch (RuntimeException | LinkageError failure) {
            state = State.FAILED;
            detail = reason + "; cleanup: " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        return false;
    }
}
