package org.academy.internal.common.ability.program;

import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PrecisionProgramImporterTest {
    @Test
    void importsLegacyGraphWithStableIdsAndOnCastEntry() {
        var source = new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(7, PrecisionGraph.NodeKind.ROSTER, 0.0, 16.0, 20.0),
                        new PrecisionGraph.Node(3, PrecisionGraph.NodeKind.MENTAL_STUPOR, 30.0, 96.0, 20.0)
                ),
                List.of(new PrecisionGraph.Edge(7, 0, 3, 0))
        );

        var imported = PrecisionProgramImporter.importGraph(source);

        assertTrue(imported.valid());
        assertEquals(3, imported.graph().nodes().size());
        assertTrue(imported.graph().nodes().stream().anyMatch(node ->
                node.id() == 0 && node.type().equals(PrecisionProgramNodeIds.ON_CAST)));
        assertTrue(imported.graph().edges().contains(new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(0, "flow"),
                new ProgramGraph.Endpoint(3, "flow")
        )));
        assertEquals(new ProgramEditorLayout.NodePosition(-80.0, 20.0),
                imported.editorLayout().nodePositions().get(0));
    }

    @Test
    void importedProgramValidatesOnlyForMentalout() {
        var source = new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(1, PrecisionGraph.NodeKind.ROSTER, 0.0, 0.0, 0.0),
                        new PrecisionGraph.Node(2, PrecisionGraph.NodeKind.MENTAL_STUPOR, 0.0, 80.0, 0.0)
                ),
                List.of(new PrecisionGraph.Edge(1, 0, 2, 0))
        );
        var imported = PrecisionProgramImporter.importGraph(source);

        var mentalout = ProgramGraphValidator.validate(
                imported.graph(),
                new ProgramCompileContext(
                        PrecisionProgramNodeCatalog.MENTALOUT,
                        Set.of(),
                        ProgramLimits.DEFAULT
                ),
                PrecisionProgramNodeCatalog.INSTANCE
        );
        var meltdowner = ProgramGraphValidator.validate(
                imported.graph(),
                new ProgramCompileContext(
                        AcademyCraft.academy("meltdowner"),
                        Set.of(),
                        ProgramLimits.DEFAULT
                ),
                PrecisionProgramNodeCatalog.INSTANCE
        );

        assertTrue(mentalout.valid(), () -> mentalout.diagnostics().toString());
        assertTrue(meltdowner.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == ProgramDiagnosticCode.CATEGORY_MISMATCH));
    }

    @Test
    void everyPrecisionKindHasOneStableUniqueResourceId() {
        var ids = Arrays.stream(PrecisionGraph.NodeKind.values())
                .map(PrecisionProgramNodeIds::id)
                .toList();

        assertEquals(55, ids.size());
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertEquals(
                AcademyCraft.academy("program/mentalout/action/mental_stupor"),
                PrecisionProgramNodeIds.id(PrecisionGraph.NodeKind.MENTAL_STUPOR)
        );
    }

    @Test
    void importedProgramExportsBackToTheNormalizedLegacyGraph() {
        var source = new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(7, PrecisionGraph.NodeKind.ROSTER, 0.0, 16.0, 20.0),
                        new PrecisionGraph.Node(3, PrecisionGraph.NodeKind.MENTAL_STUPOR, 30.0, 96.0, 20.0)
                ),
                List.of(new PrecisionGraph.Edge(7, 0, 3, 0))
        );
        var imported = PrecisionProgramImporter.importGraph(source);
        var program = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.fromString("18e20a35-c202-454d-b61b-1ce627c5185d"),
                "Round trip",
                PrecisionProgramNodeCatalog.MENTALOUT,
                imported.graph(),
                imported.editorLayout()
        );

        var exported = PrecisionProgramExporter.export(program);

        assertTrue(exported.valid());
        assertEquals(source.validate().normalized(), exported.graph());
    }

    @Test
    void legacyDuplicateBlocksImportAsCanonicalCommonNodesAndStillRoundTrip() {
        var source = new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(1, PrecisionGraph.NodeKind.CASTER, 0.0, 0.0, 0.0),
                        new PrecisionGraph.Node(2, PrecisionGraph.NodeKind.ENTITY_TO_SET,
                                0.0, 80.0, 0.0),
                        new PrecisionGraph.Node(3, PrecisionGraph.NodeKind.MENTAL_STUPOR,
                                0.0, 160.0, 0.0)
                ),
                List.of(
                        new PrecisionGraph.Edge(1, 0, 2, 0),
                        new PrecisionGraph.Edge(2, 0, 3, 0)
                )
        );

        var imported = PrecisionProgramImporter.importGraph(source);
        var singleton = imported.graph().nodes().stream()
                .filter(node -> node.id() == 2).findFirst().orElseThrow();
        var program = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.fromString("948eaf31-7c40-45f5-95c1-bde37a40ee1b"),
                "Canonical aliases",
                PrecisionProgramNodeCatalog.MENTALOUT,
                imported.graph(),
                imported.editorLayout()
        );

        assertEquals(CommonProgramNodeIds.collection("entity", "singleton"), singleton.type());
        assertTrue(imported.graph().edges().stream().anyMatch(edge ->
                edge.from().nodeId() == 1 && edge.from().port().equals("entity")
                        && edge.to().nodeId() == 2 && edge.to().port().equals("value")));
        assertTrue(imported.graph().edges().stream().anyMatch(edge ->
                edge.from().nodeId() == 2 && edge.from().port().equals("values")
                        && edge.to().nodeId() == 3 && edge.to().port().equals("subjects")));
        var exported = PrecisionProgramExporter.export(program);
        assertTrue(exported.valid());
        assertEquals(source.validate().normalized(), exported.graph());
    }

    @Test
    void editableImportPreservesAnIncompleteCanvasForLaterRepair() {
        var incomplete = new PrecisionGraph(
                List.of(new PrecisionGraph.Node(
                        4,
                        PrecisionGraph.NodeKind.MENTAL_STUPOR,
                        20.0,
                        48.0,
                        32.0
                )),
                List.of()
        );

        var imported = PrecisionProgramImporter.importEditableGraph(incomplete);
        var program = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.fromString("72c60593-4347-4cc7-b831-e1082ac5a655"),
                "Incomplete",
                PrecisionProgramNodeCatalog.MENTALOUT,
                imported.graph(),
                imported.editorLayout()
        );
        var document = new ProgramEditorDocument(
                program,
                AbilityProgramDefinitions.mentalout(),
                Set.of()
        );

        assertTrue(imported.valid());
        assertTrue(imported.graph().nodes().stream().anyMatch(node ->
                node.type().equals(PrecisionProgramNodeIds.ON_CAST)));
        assertFalse(document.validation().valid());
        assertTrue(document.validation().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == ProgramDiagnosticCode.MISSING_INPUT));
    }
}
