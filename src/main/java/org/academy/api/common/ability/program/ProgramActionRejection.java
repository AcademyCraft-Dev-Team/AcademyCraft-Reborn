package org.academy.api.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A stable, player-facing reason why a program action cannot execute.
 *
 * <p>Extensions may throw this from action validation or execution. The program runtime preserves
 * the reason through transaction wrappers and translates it for the client; exception messages
 * and the optional underlying cause remain server-side diagnostics.</p>
 */
public final class ProgramActionRejection extends RuntimeException {
    private final Reason reason;

    public ProgramActionRejection(Reason reason) {
        this(reason, null);
    }

    public ProgramActionRejection(Reason reason, @Nullable Throwable cause) {
        super(Objects.requireNonNull(reason, "reason").name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        PREPARING,
        STALE_BINDING,
        CONFLICTING_INPUTS,
        INVALID_INPUT,
        TARGET_UNREACHABLE,
        ACTION_BUSY,
        NATIVE_CAST_REJECTED,
        UNAVAILABLE
    }
}
