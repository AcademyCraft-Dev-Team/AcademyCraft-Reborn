package org.academy.internal.common.ability.electromaster.program;

import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.program.migrate.ProgramGraphMigrator;

/**
 * Explicit Electromaster graph migrations. The legacy {@code action/magnetic_move} node keeps
 * its ports and configuration; its block target is rejected at runtime and no rewrite exists
 * because the unified pull node has no equivalent power field, so automatic migration would
 * silently drop that setting. The audit records this boundary instead of refusing the graph.
 */
public final class ElectromasterProgramGraphMigrations {
    public static final String LEGACY_MAGNETIC_BLOCK_TARGET =
            "legacy_magnetic_block_target_use_structure_nodes";

    private ElectromasterProgramGraphMigrations() {
    }

    public static ProgramGraphMigrator create() {
        return ProgramGraphMigrator.builder()
                .audit(ProgramGraphMigrator.audit(
                        ElectromasterProgramNodeIds.MAGNETIC_MOVE,
                        node -> blockTarget(node) ? LEGACY_MAGNETIC_BLOCK_TARGET : null))
                .build();
    }

    private static boolean blockTarget(ProgramGraph.Node node) {
        var configuration = node.configuration();
        if (!configuration.isJsonObject()) return false;
        var targetType = configuration.getAsJsonObject().get("target_type");
        return targetType != null && targetType.isJsonPrimitive()
                && "block".equals(targetType.getAsString());
    }
}
