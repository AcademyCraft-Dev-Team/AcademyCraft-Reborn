package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.mentalout.skills.lv5.PrecisionOperation;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Imports the four legacy Precision Operation slots into the current program book.
 */
public final class PrecisionProgramBookMigrator {
    private PrecisionProgramBookMigrator() {
    }

    public static MigrationResult migrate(UUID ownerId, PrecisionOperation.Data source) {
        if (ownerId == null) throw new IllegalArgumentException("Program book owner cannot be null");
        if (source == null) source = new PrecisionOperation.Data();
        PrecisionOperation.normalizeLegacyData(source);
        var slots = new ArrayList<ProgramBook.Slot>(AbilityProgramManager.SLOT_COUNT);
        var failures = new ArrayList<SlotFailure>();
        for (var slot = 0; slot < 4; slot++) {
            var precisionGraph = source.legacySlot(slot);
            if (precisionGraph.nodes().isEmpty()) {
                slots.add(ProgramBook.Slot.EMPTY);
                continue;
            }
            var imported = importProgram(ownerId, slot, precisionGraph, null);
            if (!imported.valid()) {
                failures.add(new SlotFailure(slot, imported.diagnostic));
                slots.add(ProgramBook.Slot.EMPTY);
                continue;
            }
            slots.add(new ProgramBook.Slot(imported.program));
        }
        while (slots.size() < AbilityProgramManager.SLOT_COUNT) {
            slots.add(ProgramBook.Slot.EMPTY);
        }
        return new MigrationResult(
                new ProgramBook(
                        ProgramBook.CURRENT_SCHEMA_VERSION,
                        source.revision(),
                        0,
                        slots
                ),
                failures
        );
    }

    public static ImportProgramResult importProgram(
            UUID ownerId,
            int slot,
            PrecisionGraph precisionGraph,
            @Nullable AbilityProgram existing
    ) {
        if (precisionGraph == null || precisionGraph.nodes().isEmpty()) {
            return new ImportProgramResult(null, PrecisionGraph.Diagnostic.OK);
        }
        var imported = PrecisionProgramImporter.importGraph(precisionGraph);
        if (!imported.valid()) return new ImportProgramResult(null, imported.diagnostic());
        var programId = existing == null
                ? UUID.nameUUIDFromBytes((
                "academy:precision_operation:" + ownerId + ":" + slot
        ).getBytes(StandardCharsets.UTF_8))
                : existing.id();
        return new ImportProgramResult(new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                programId,
                existing == null ? "Precision " + (slot + 1) : existing.name(),
                PrecisionProgramNodeCatalog.MENTALOUT,
                imported.graph(),
                imported.editorLayout()
        ), PrecisionGraph.Diagnostic.OK);
    }

    public record MigrationResult(ProgramBook book, List<SlotFailure> failures) {
        public MigrationResult {
            failures = List.copyOf(failures);
        }

        public boolean complete() {
            return failures.isEmpty();
        }
    }

    public record SlotFailure(int slot, PrecisionGraph.Diagnostic diagnostic) {
    }

    public record ImportProgramResult(
            @Nullable AbilityProgram program,
            PrecisionGraph.Diagnostic diagnostic
    ) {
        public boolean valid() {
            return diagnostic == PrecisionGraph.Diagnostic.OK;
        }
    }
}
