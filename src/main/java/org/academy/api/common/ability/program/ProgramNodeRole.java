package org.academy.api.common.ability.program;

/**
 * Structural role used by the compiler to validate control-flow reachability.
 */
public enum ProgramNodeRole {
    VALUE(false),
    QUERY(false),
    ENTRY(false),
    CONTROL(true),
    ACTION(true),
    SUSPEND(true);

    private final boolean requiresFlow;

    ProgramNodeRole(boolean requiresFlow) {
        this.requiresFlow = requiresFlow;
    }

    public boolean requiresFlow() {
        return requiresFlow;
    }
}
