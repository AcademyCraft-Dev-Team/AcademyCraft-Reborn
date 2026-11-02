package org.academy.api.server.ability.spell;

import java.util.Objects;

/** Stable geometry rejection categories for spell callers, independent of the program UI. */
public final class SpellGeometryException extends IllegalArgumentException {
    public enum Reason { INVALID_INPUT, CONFLICTING_INPUTS, TARGET_UNREACHABLE, UNAVAILABLE }

    private final Reason reason;

    public SpellGeometryException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() { return reason; }
}
