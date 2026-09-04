package org.academy.internal.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Immutable trigger snapshot retained for the complete lifetime of one program invocation.
 *
 * <p>Event-local values must live here instead of in event-handler thread state so a program can
 * still query them after yielding and resuming on a later server tick.</p>
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
}
