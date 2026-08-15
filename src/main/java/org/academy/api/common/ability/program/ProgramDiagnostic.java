package org.academy.api.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record ProgramDiagnostic(
        ProgramDiagnosticCode code,
        int nodeId,
        @Nullable String port
) {
    public ProgramDiagnostic {
        Objects.requireNonNull(code, "code");
    }

    public static ProgramDiagnostic graph(ProgramDiagnosticCode code) {
        return new ProgramDiagnostic(code, -1, null);
    }

    public static ProgramDiagnostic node(ProgramDiagnosticCode code, int nodeId) {
        return new ProgramDiagnostic(code, nodeId, null);
    }
}
