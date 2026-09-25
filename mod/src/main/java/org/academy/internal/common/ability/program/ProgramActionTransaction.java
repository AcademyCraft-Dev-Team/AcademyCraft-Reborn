package org.academy.internal.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded transaction for world-affecting program actions.
 *
 * <p>Actions settle in staging order. Deferred transactions validate and apply each action as one
 * ordered step during commit; sequential transactions settle each action as soon as it is staged.
 * Successfully applied actions return compensators, which are invoked in reverse order if a later
 * action or the surrounding program fails. Once the caller has transferred ownership of the
 * effects to its long-lived runtime, it must call {@link #release()}.</p>
 */
public final class ProgramActionTransaction {
    public static final int DEFAULT_MAX_ACTIONS = 256;

    private final int maxActions;
    private final SettlementMode settlementMode;
    private final List<StagedAction> staged = new ArrayList<>();
    private final List<AppliedAction> applied = new ArrayList<>();
    private @Nullable Result lastFailure;
    private State state = State.OPEN;

    public ProgramActionTransaction() {
        this(DEFAULT_MAX_ACTIONS, SettlementMode.DEFERRED);
    }

    public ProgramActionTransaction(int maxActions) {
        this(maxActions, SettlementMode.DEFERRED);
    }

    public ProgramActionTransaction(int maxActions, SettlementMode settlementMode) {
        if (maxActions < 1) throw new IllegalArgumentException("Action limit must be positive");
        this.maxActions = maxActions;
        this.settlementMode = Objects.requireNonNull(settlementMode, "settlementMode");
    }

    /**
     * Creates a transaction whose actions settle as their flow nodes are reached.
     */
    public static ProgramActionTransaction sequential() {
        return new ProgramActionTransaction(DEFAULT_MAX_ACTIONS, SettlementMode.SEQUENTIAL);
    }

    public void stage(int nodeId, ProgramAction action) {
        requireState(State.OPEN);
        if (nodeId < 0) throw new IllegalArgumentException("Program action node id cannot be negative");
        if (staged.size() >= maxActions) {
            throw new IllegalStateException("Program action transaction exceeds its action limit");
        }
        var entry = new StagedAction(nodeId, Objects.requireNonNull(action, "action"));
        staged.add(entry);
        if (settlementMode == SettlementMode.SEQUENTIAL) settle(entry);
    }

    public Result commit() {
        requireState(State.OPEN);
        if (settlementMode == SettlementMode.SEQUENTIAL) {
            state = State.COMMITTED;
            return Result.success(state, Phase.APPLY);
        }
        for (var entry : staged) {
            var settled = settle(entry);
            if (!settled.successful()) return settled;
        }
        state = State.COMMITTED;
        return Result.success(state, Phase.APPLY);
    }

    /**
     * Rolls back an unfinished sequential execution after VM failure or cancellation.
     */
    public Result abort() {
        if (state == State.FAILED || state == State.ROLLED_BACK) {
            return lastFailure == null ? Result.success(state, Phase.ROLLBACK) : lastFailure;
        }
        if (state == State.COMMITTED) return rollback();
        requireState(State.OPEN);
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

    public SettlementMode settlementMode() {
        return settlementMode;
    }

    private Result settle(StagedAction entry) {
        try {
            entry.action.validate();
        } catch (Exception exception) {
            return fail(entry, Phase.VALIDATE, exception);
        }
        try {
            var undo = Objects.requireNonNull(
                    entry.action.apply(),
                    "Program action returned a null compensator"
            );
            applied.add(new AppliedAction(entry.nodeId, undo));
            return Result.success(state, Phase.APPLY);
        } catch (Exception exception) {
            return fail(entry, Phase.APPLY, exception);
        }
    }

    private Result fail(StagedAction entry, Phase phase, Exception exception) {
        var rollbackFailures = rollbackApplied();
        state = State.FAILED;
        var result = Result.failure(state, phase, entry.nodeId, exception, rollbackFailures);
        lastFailure = result;
        if (settlementMode == SettlementMode.SEQUENTIAL) throw new SettlementException(result);
        return result;
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

    public enum SettlementMode {
        DEFERRED,
        SEQUENTIAL
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

    /**
     * Runtime failure raised when immediate sequential settlement rejects an action.
     */
    public static final class SettlementException extends RuntimeException {
        private final Result result;

        private SettlementException(Result result) {
            super("Program action settlement failed at node " + result.nodeId(), result.cause());
            this.result = result;
        }

        public Result result() {
            return result;
        }
    }

    private record StagedAction(int nodeId, ProgramAction action) {
    }

    private record AppliedAction(int nodeId, Undo undo) {
    }
}
