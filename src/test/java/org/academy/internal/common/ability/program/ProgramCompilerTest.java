package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramCompileContext;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramCompilerTest {
    @Test
    void compilesImportedPrecisionGraphIntoNamedSlotsAndFlowTargets() {
        var source = new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(4, PrecisionGraph.NodeKind.ROSTER, 0.0, 0.0, 0.0),
                        new PrecisionGraph.Node(9, PrecisionGraph.NodeKind.MENTAL_STUPOR, 20.0, 80.0, 0.0)
                ),
                List.of(new PrecisionGraph.Edge(4, 0, 9, 0))
        );
        var imported = PrecisionProgramImporter.importGraph(source);

        var result = ProgramCompiler.compile(
                imported.graph(),
                new ProgramCompileContext(
                        PrecisionProgramNodeCatalog.MENTALOUT,
                        Set.of(),
                        ProgramLimits.DEFAULT
                ),
                PrecisionProgramNodeCatalog.INSTANCE
        );

        assertTrue(result.valid(), () -> result.diagnostics().toString());
        var program = result.program();
        assertEquals(0, program.entryNodeId());
        assertEquals(9, program.flowTarget(0, "flow"));
        assertEquals(
                List.of(new CompiledProgram.OutputKey(4, "entities")),
                program.inputs(9, "subjects")
        );
        assertEquals(List.of(0, 4, 9), program.dataOrder());
    }

    @Test
    void rejectsEmptyProgramAtDeploymentCompileStage() {
        var result = ProgramCompiler.compile(
                ProgramGraph.EMPTY,
                new ProgramCompileContext(
                        PrecisionProgramNodeCatalog.MENTALOUT,
                        Set.of(),
                        ProgramLimits.DEFAULT
                ),
                PrecisionProgramNodeCatalog.INSTANCE
        );

        assertEquals(ProgramDiagnosticCode.EMPTY_PROGRAM, result.diagnostics().getFirst().code());
    }
}
