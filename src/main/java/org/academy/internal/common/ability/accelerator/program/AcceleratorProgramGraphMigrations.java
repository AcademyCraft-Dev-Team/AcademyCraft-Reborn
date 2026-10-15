package org.academy.internal.common.ability.accelerator.program;

import org.academy.internal.common.ability.program.migrate.ProgramGraphMigrator;

/**
 * Explicit Accelerator graph migrations. Vector structure nodes entered the catalog already in
 * the unified family, so no legacy structure action needs rewriting; this migrator exists as the
 * category's stable extension point for future node schema upgrades.
 */
public final class AcceleratorProgramGraphMigrations {
    private AcceleratorProgramGraphMigrations() {
    }

    public static ProgramGraphMigrator create() {
        return ProgramGraphMigrator.EMPTY;
    }
}
