package org.academy.internal.common.ability.program.migrate;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramGraphMigrations;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeCatalog;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramGraphMigrations;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramNodeCatalog;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramGraphMigrations;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramNodeCatalog;
import org.academy.internal.common.ability.program.registry.ProgramNodeLookup;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Category registry of the explicit program-graph migrators. Each programmable category owns
 * its own migrator; categories without known legacy formats migrate as a verified no-op.
 */
public final class ProgramGraphMigrations {
    private static final Map<Identifier, ProgramGraphMigrator> MIGRATORS = new ConcurrentHashMap<>();

    private ProgramGraphMigrations() {
    }

    public static ProgramGraphMigrator forCategory(Identifier category) {
        Objects.requireNonNull(category, "category");
        return MIGRATORS.computeIfAbsent(category, ProgramGraphMigrations::create);
    }

    public static ProgramGraphMigrator.Result migrate(
            AbilityProgram program,
            ProgramNodeLookup lookup
    ) {
        var result = forCategory(program.category()).migrate(program, lookup);
        result.diagnostics().forEach(diagnostic -> AcademyCraft.LOGGER.info(
                "Program migration [{}] node {} {}: {}",
                program.category(), diagnostic.nodeId(), diagnostic.code(), diagnostic.detail()));
        return result;
    }

    /**
     * Migrates every present slot of a decoded book while keeping the book envelope version,
     * revision and selection untouched. Absent slots are preserved.
     */
    public static ProgramBook migrateBook(
            ProgramBook book,
            Identifier category,
            ProgramNodeLookup lookup
    ) {
        Objects.requireNonNull(book, "book");
        var slots = new ArrayList<ProgramBook.Slot>(book.slots().size());
        var changed = false;
        for (var slot : book.slots()) {
            var program = slot.program();
            if (program == null) {
                slots.add(slot);
                continue;
            }
            var result = migrate(program, lookup);
            slots.add(new ProgramBook.Slot(result.program()));
            changed |= result.changed();
        }
        return changed ? new ProgramBook(
                book.schemaVersion(), book.revision(), book.selectedSlot(), slots) : book;
    }

    private static ProgramGraphMigrator create(Identifier category) {
        if (category.equals(AeromanipProgramNodeCatalog.AEROMANIP)) {
            return AeromanipProgramGraphMigrations.create();
        }
        if (category.equals(AcceleratorProgramNodeCatalog.ACCELERATOR)) {
            return AcceleratorProgramGraphMigrations.create();
        }
        if (category.equals(ElectromasterProgramNodeCatalog.ELECTROMASTER)) {
            return ElectromasterProgramGraphMigrations.create();
        }
        return ProgramGraphMigrator.EMPTY;
    }

    /** Test and integration observation of the registered category migrators. */
    public static List<Identifier> registeredCategories() {
        forCategory(AeromanipProgramNodeCatalog.AEROMANIP);
        forCategory(AcceleratorProgramNodeCatalog.ACCELERATOR);
        forCategory(ElectromasterProgramNodeCatalog.ELECTROMASTER);
        return List.copyOf(MIGRATORS.keySet());
    }
}
