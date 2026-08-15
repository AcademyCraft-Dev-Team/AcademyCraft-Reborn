package org.academy.api.common.ability.program;

import java.util.List;

public record ProgramValidationResult(List<ProgramDiagnostic> diagnostics) {
    public ProgramValidationResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean valid() {
        return diagnostics.isEmpty();
    }
}
