package org.academy.internal.common.ability.program;

import org.academy.internal.common.ability.program.registry.CommonProgramNodeCatalog;


import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Trigger snapshot retained for the complete lifetime of one program invocation.
 *
 * <p>Event-local values must live here instead of in event-handler thread state so a program can
 * still query them after yielding and resuming on a later server tick. The trace and its
 * completion listener are bounded diagnostics for that same invocation.</p>
 */
public final class ProgramInvocationContext {
    private final UUID programId;
    private final int slot;
    private final ProgramTriggers.Type trigger;
    private final CommonProgramNodeCatalog.MovementCondition movement;
    private final long loopIndex;
    private final @Nullable Object damageAttacker;
    private final @Nullable Float damageAmount;
    private final @Nullable Object meleeTarget;
    private final @Nullable String chatMessage;
    private final ProgramRunTrace runTrace = new ProgramRunTrace();
    private @Nullable Consumer<ProgramVmResult> runCompletionListener;

    public ProgramInvocationContext(
            UUID programId,
            int slot,
            ProgramTriggers.Type trigger,
            CommonProgramNodeCatalog.MovementCondition movement,
            long loopIndex,
            @Nullable Object damageAttacker,
            @Nullable Float damageAmount,
            @Nullable Object meleeTarget
    ) {
        this(programId, slot, trigger, movement, loopIndex,
                damageAttacker, damageAmount, meleeTarget, null);
    }

    public ProgramInvocationContext(
            UUID programId,
            int slot,
            ProgramTriggers.Type trigger,
            CommonProgramNodeCatalog.MovementCondition movement,
            long loopIndex,
            @Nullable Object damageAttacker,
            @Nullable Float damageAmount,
            @Nullable Object meleeTarget,
            @Nullable String chatMessage
    ) {
        this.programId = Objects.requireNonNull(programId, "programId");
        if (slot < 0 || slot >= AbilityProgramManager.SLOT_COUNT) {
            throw new IllegalArgumentException("Program slot is out of range");
        }
        this.slot = slot;
        this.trigger = trigger;
        this.movement = movement;
        if (loopIndex < 0) throw new IllegalArgumentException("Loop index cannot be negative");
        this.loopIndex = loopIndex;
        this.damageAttacker = damageAttacker;
        this.damageAmount = damageAmount;
        this.meleeTarget = meleeTarget;
        this.chatMessage = chatMessage;
    }

    public UUID programId() {
        return programId;
    }

    public int slot() {
        return slot;
    }

    public Optional<ProgramTriggers.Type> trigger() {
        return Optional.ofNullable(trigger);
    }

    public Optional<CommonProgramNodeCatalog.MovementCondition> movement() {
        return Optional.ofNullable(movement);
    }

    /** Zero-based count for loop-trigger executions; zero for all other trigger types. */
    public long loopIndex() {
        return loopIndex;
    }

    public Optional<Object> damageAttacker() {
        return Optional.ofNullable(damageAttacker);
    }

    public OptionalDouble damageAmount() {
        return damageAmount == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(damageAmount.doubleValue());
    }

    public Optional<Object> meleeTarget() {
        return Optional.ofNullable(meleeTarget);
    }

    public Optional<String> chatMessage() {
        return Optional.ofNullable(chatMessage);
    }

    public ProgramRunTrace runTrace() {
        return runTrace;
    }

    void onRunComplete(Consumer<ProgramVmResult> listener) {
        runCompletionListener = Objects.requireNonNull(listener);
    }

    void reportRunResult(ProgramVmResult result) {
        if (runCompletionListener == null) return;
        if (result.status() != ProgramVmResult.Status.COMPLETED
                && result.status() != ProgramVmResult.Status.FAILED) return;
        var listener = runCompletionListener;
        runCompletionListener = null;
        listener.accept(result);
    }
}
