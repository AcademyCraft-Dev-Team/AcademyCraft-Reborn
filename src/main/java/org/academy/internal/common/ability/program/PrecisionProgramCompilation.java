package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;

import java.util.Set;

/** Shared compiler entry point for mixed common and Mentalout-category program graphs. */
public final class PrecisionProgramCompilation {
    private PrecisionProgramCompilation() {
    }

    public static ProgramCompileResult compile(PrecisionGraph graph) {
        var imported = PrecisionProgramImporter.importGraph(graph);
        if (!imported.valid()) {
            return new ProgramCompileResult(
                    null,
                    java.util.List.of(org.academy.api.common.ability.program.ProgramDiagnostic.graph(
                            org.academy.api.common.ability.program.ProgramDiagnosticCode.INVALID_CONFIGURATION
                    ))
            );
        }
        return compile(imported.graph());
    }

    public static ProgramCompileResult compile(ProgramGraph graph) {
        return AbilityProgramDefinitions.mentalout().compile(graph, Set.of());
    }

    public static ProgramCompileResult compile(AbilityProgram program) {
        return AbilityProgramDefinitions.mentalout().compile(program, Set.of());
    }
}
