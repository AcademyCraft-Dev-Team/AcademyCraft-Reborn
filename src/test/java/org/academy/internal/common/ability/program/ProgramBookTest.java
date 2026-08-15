package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.mentalout.skills.lv5.PrecisionOperation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramBookTest {
    @Test
    void replacementIsImmutableAndIncrementsSemanticRevision() {
        var empty = ProgramBook.empty(4);
        var program = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                "Test",
                PrecisionProgramNodeCatalog.MENTALOUT,
                ProgramGraph.EMPTY,
                ProgramEditorLayout.EMPTY
        );

        var changed = empty.replaceSlot(2, program);

        assertNull(empty.slot(2).program());
        assertEquals(program, changed.slot(2).program());
        assertEquals(0, empty.revision());
        assertEquals(1, changed.revision());
        assertEquals(1, changed.select(2).revision());
        assertEquals(2, changed.select(2).selectedSlot());
    }

    @Test
    void migratesPrecisionSlotsAndPreservesRevisionAndLayout() {
        var data = new PrecisionOperation.Data();
        data.replaceSlot(1, precisionGraph());
        var owner = UUID.fromString("6c11a1e7-fca1-42af-9c97-c74a7bd3b2a0");

        var migrated = PrecisionProgramBookMigrator.migrate(owner, data);

        assertTrue(migrated.complete());
        assertEquals(data.revision(), migrated.book().revision());
        assertTrue(migrated.book().slot(0).empty());
        var program = migrated.book().slot(1).program();
        assertEquals(PrecisionProgramNodeCatalog.MENTALOUT, program.category());
        assertTrue(program.graph().nodes().stream().anyMatch(node ->
                node.type().equals(PrecisionProgramNodeIds.ON_CAST)));
        assertEquals(16.0, program.editorLayout().nodePositions().get(7).x());
    }

    @Test
    void migratedProgramIdsAreStablePerOwnerAndSlot() {
        var data = new PrecisionOperation.Data();
        data.replaceSlot(0, precisionGraph());
        var firstOwner = UUID.fromString("71c69c7a-6524-4f2f-9a41-d716280f4d3a");
        var otherOwner = UUID.fromString("cb291e61-847d-458b-9765-30c05ccb0a44");

        var first = PrecisionProgramBookMigrator.migrate(firstOwner, data)
                .book().slot(0).program().id();
        var repeated = PrecisionProgramBookMigrator.migrate(firstOwner, data)
                .book().slot(0).program().id();
        var other = PrecisionProgramBookMigrator.migrate(otherOwner, data)
                .book().slot(0).program().id();

        assertEquals(first, repeated);
        assertNotEquals(first, other);
    }

    @Test
    void copyingLegacyDataDefersMigrationUntilTheRealOwnerIsKnown() {
        var data = new PrecisionOperation.Data();
        data.replaceSlot(0, precisionGraph());
        var copy = data.copy();
        var firstOwner = UUID.fromString("a7227268-f8c0-4e33-8830-bae262352796");
        var otherOwner = UUID.fromString("8e797611-d098-4862-8e26-e3933cb2ca00");

        var first = PrecisionProgramBookMigrator.migrate(firstOwner, data)
                .book().slot(0).program().id();
        var other = PrecisionProgramBookMigrator.migrate(otherOwner, copy)
                .book().slot(0).program().id();

        assertNotEquals(first, other);
    }

    private static PrecisionGraph precisionGraph() {
        return new PrecisionGraph(
                List.of(
                        new PrecisionGraph.Node(7, PrecisionGraph.NodeKind.ROSTER, 0.0, 16.0, 20.0),
                        new PrecisionGraph.Node(3, PrecisionGraph.NodeKind.MENTAL_STUPOR, 20.0, 96.0, 20.0)
                ),
                List.of(new PrecisionGraph.Edge(7, 0, 3, 0))
        );
    }
}
