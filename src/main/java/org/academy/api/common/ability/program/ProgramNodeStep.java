package org.academy.api.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Immutable result of one program-node invocation.
 */
public record ProgramNodeStep(
        Directive directive,
        Map<String, ProgramValue<?>> outputs,
        @Nullable String flowOutput,
        long delayTicks
) {
    public ProgramNodeStep {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        if (delayTicks < 0) throw new IllegalArgumentException("Program delay cannot be negative");
        if (directive == Directive.YIELD && delayTicks < 1) {
            throw new IllegalArgumentException("Yielding program node must delay at least one tick");
        }
    }

    public static ProgramNodeStep data(Map<String, ProgramValue<?>> outputs) {
        return new ProgramNodeStep(Directive.DATA, outputs, null, 0);
    }

    public static ProgramNodeStep next(String flowOutput) {
        return next(flowOutput, Map.of());
    }

    public static ProgramNodeStep next(
            String flowOutput,
            Map<String, ProgramValue<?>> outputs
    ) {
        return new ProgramNodeStep(Directive.CONTINUE, outputs, flowOutput, 0);
    }

    public static ProgramNodeStep yield(String flowOutput, long delayTicks) {
        return new ProgramNodeStep(Directive.YIELD, Map.of(), flowOutput, delayTicks);
    }

    /**
     * Enters a structured flow branch and resumes this node when that branch reaches its end.
     */
    public static ProgramNodeStep call(
            String flowOutput,
            Map<String, ProgramValue<?>> outputs
    ) {
        return new ProgramNodeStep(Directive.CALL, outputs, flowOutput, 0);
    }

    public static ProgramNodeStep stop() {
        return new ProgramNodeStep(Directive.STOP, Map.of(), null, 0);
    }

    /** Skips the remainder of the nearest enclosing structured loop body. */
    public static ProgramNodeStep continueLoop() {
        return new ProgramNodeStep(Directive.LOOP_CONTINUE, Map.of(), null, 0);
    }

    /** Ends the nearest enclosing structured loop and follows its done output. */
    public static ProgramNodeStep breakLoop() {
        return new ProgramNodeStep(Directive.LOOP_BREAK, Map.of(), null, 0);
    }

    public enum Directive {
        DATA,
        CONTINUE,
        CALL,
        YIELD,
        LOOP_CONTINUE,
        LOOP_BREAK,
        STOP
    }
}
