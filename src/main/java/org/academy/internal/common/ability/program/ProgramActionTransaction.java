package org.academy.internal.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded transaction for world-affecting program actions.
 *
 * <p>Every staged action is validated before the first action is applied. Successfully applied
 * actions return compensators, which are invoked in reverse order if a later action fails. Once
 * the caller has transferred ownership of the effects to its long-lived runtime, it must call
 * {@link #release()}.</p>
 */
public final class ProgramActionTransaction {
    public static final int DEFAULT_MAX_ACTIONS = 256;

    private final int maxActions;
    private final List<StagedAction> staged = new ArrayList<>();
    private final List<AppliedAction> applied = new ArrayList<>();
    private State state = State.OPEN;

    public ProgramActionTransaction() {
        this(DEFAULT_MAX_ACTIONS);
    }

    public ProgramActionTransaction(int maxActions) {
        if (maxActions < 1) throw new IllegalArgumentException("Action limit must be positive");
        this.maxActions = maxActions;
    }

    public void stage(int nodeId, ProgramAction action) {
        requireState(State.OPEN);
        if (nodeId < 0) throw new IllegalArgumentException("Program action node id cannot be negative");
        if (staged.size() >= maxActions) {
            throw new IllegalStateException("Program action transaction exceeds its action limit");
        }
        staged.add(new StagedAction(nodeId, Objects.requireNonNull(action, "action")));
    }

    public Result commit() {
        requireState(State.OPEN);
        for (var entry : staged) {
            try {
                entry.action.validate();
            } catch (Exception exception) {
                state = State.FAILED;
                return Result.failure(state, Phase.VALIDATE, entry.nodeId, exception, 0);
            }
        }

        for (var entry : staged) {
            try {
                var undo = Objects.requireNonNull(
                        entry.action.apply(),
                        "Program action returned a null compensator"
                );
                applied.add(new AppliedAction(entry.nodeId, undo));
            } catch (Exception exception) {
                var rollbackFailures = rollbackApplied();
                state = State.FAILED;
                return Result.failure(
                        state,
                        Phase.APPLY,
                        entry.nodeId,
                        exception,
                        rollbackFailures
                );
            }
        }
        state = State.COMMITTED;
        return Result.success(state, Phase.APPLY);
    }

    /**
     * Compensates an already committed transaction. Calling it after an apply failure is harmless.
     */
    public Result rollback() {
        if (state == State.ROLLED_BACK || state == State.FAILED) {
            return Result.success(state, Phase.ROLLBACK);
        }
        requireState(State.COMMITTED);
        var failures = rollbackApplied();
        state = State.ROLLED_BACK;
        return failures == 0
                ? Result.success(state, Phase.ROLLBACK)
                : Result.failure(
                state,
                Phase.ROLLBACK,
                -1,
                new IllegalStateException("One or more program compensators failed"),
                failures
        );
    }

    /**
     * Transfers responsibility for committed effects to the caller and discards compensators.
     */
    public void release() {
        requireState(State.COMMITTED);
        applied.clear();
        state = State.RELEASED;
    }

    public State state() {
        return state;
    }

    public int size() {
        return staged.size();
    }

    private int rollbackApplied() {
        var failures = 0;
        for (var index = applied.size() - 1; index >= 0; index--) {
            try {
                applied.get(index).undo.close();
            } catch (Exception exception) {
                failures++;
            }
        }
        applied.clear();
        return failures;
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Program action transaction is " + state + ", expected " + expected
            );
        }
    }

    @FunctionalInterface
    public interface ProgramAction {
        default void validate() throws Exception {
        }

        Undo apply() throws Exception;
    }

    @FunctionalInterface
    public interface Undo extends AutoCloseable {
        Undo NONE = () -> {
        };

        @Override
        void close() throws Exception;
    }

    public enum State {
        OPEN,
        COMMITTED,
        ROLLED_BACK,
        RELEASED,
        FAILED
    }

    public enum Phase {
        VALIDATE,
        APPLY,
        ROLLBACK
    }

    public record Result(
            boolean successful,
            State state,
            Phase phase,
            int nodeId,
            @Nullable Throwable cause,
            int rollbackFailures
    ) {
        private static Result success(State state, Phase phase) {
            return new Result(true, state, phase, -1, null, 0);
        }

        private static Result failure(
                State state,
                Phase phase,
                int nodeId,
                Throwable cause,
                int rollbackFailures
        ) {
            return new Result(false, state, phase, nodeId, cause, rollbackFailures);
        }
    }

    private record StagedAction(int nodeId, ProgramAction action) {
    }

    private record AppliedAction(int nodeId, Undo undo) {
    }
}
