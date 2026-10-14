package org.academy.internal.common.ability.program;

import org.jspecify.annotations.Nullable;

public record ProgramVmResult(
        Status status,
        int nodeId,
        ProgramVmDiagnostic diagnostic,
        @Nullable String port,
        @Nullable String detail
) {
    public ProgramVmResult(Status status, int nodeId, ProgramVmDiagnostic diagnostic) {
        this(status, nodeId, diagnostic, null, null);
    }

    public enum Status {
        COMPLETED,
        SUSPENDED,
        FUEL_EXHAUSTED,
        FAILED
    }
}
