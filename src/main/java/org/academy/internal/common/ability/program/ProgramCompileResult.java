package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramDiagnostic;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record ProgramCompileResult(
        @Nullable CompiledProgram program,
        List<ProgramDiagnostic> diagnostics
) {
    public ProgramCompileResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean valid() {
        return program != null && diagnostics.isEmpty();
    }
}
