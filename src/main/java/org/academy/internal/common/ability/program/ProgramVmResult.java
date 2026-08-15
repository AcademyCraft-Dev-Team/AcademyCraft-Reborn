package org.academy.internal.common.ability.program;

public record ProgramVmResult(
        Status status,
        int nodeId,
        ProgramVmDiagnostic diagnostic
) {
    public enum Status {
        COMPLETED,
        SUSPENDED,
        FUEL_EXHAUSTED,
        FAILED
    }
}
