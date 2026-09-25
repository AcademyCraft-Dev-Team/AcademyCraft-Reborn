package org.academy.internal.common.ability.mentalout.precision;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.internal.common.ability.program.*;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Assembles Mentalout-specific nodes with the shared program algebra.
 */
public final class MentaloutProgramDefinition {
    private MentaloutProgramDefinition() {
    }

    public static AbilityProgramDefinition create() {
        var editor = ProgramEditorNodeCatalog.builder(PrecisionProgramNodeCatalog.MENTALOUT)
                .includeCommonNodes();
        PrecisionProgramNodeCatalog.INSTANCE.types().forEach((id, type) -> {
            var kind = PrecisionProgramNodeIds.kind(id);
            var configuration = new JsonObject();
            if (kind != null) configuration.addProperty("parameter", kind.defaultParameter());
            var suffix = kind == null
                    ? id.getPath().substring(id.getPath().lastIndexOf('/') + 1)
                    : kind.name().toLowerCase(Locale.ROOT);
            editor.add(
                    id,
                    type,
                    configuration,
                    group(id, kind),
                    "screen.academy.precision_operation.node." + suffix,
                    "screen.academy.precision_operation.port.",
                    kind == null || !PrecisionProgramAliases.isLegacyAlias(kind),
                    kind
            );
        });
        return new AbilityProgramDefinition(
                PrecisionProgramNodeCatalog.MENTALOUT,
                PrecisionProgramNodeCatalog.INSTANCE.types(),
                PrecisionProgramExecutionBridge.categoryExecutors(),
                editor.build(),
                ProgramLimits.DEFAULT
        );
    }

    private static ProgramEditorNodeCatalog.Group group(
            Identifier id,
            PrecisionGraph.@Nullable NodeKind kind
    ) {
        if (id.equals(PrecisionProgramNodeIds.ON_CAST)) return ProgramEditorNodeCatalog.Group.FLOW;
        if (kind == null) return ProgramEditorNodeCatalog.Group.VALUE;
        if (kind == PrecisionGraph.NodeKind.SIGHT_POSITION
                || kind == PrecisionGraph.NodeKind.ENTITY_POSITION
                || kind == PrecisionGraph.NodeKind.DIRECTION_BETWEEN
                || kind == PrecisionGraph.NodeKind.POSITION_OFFSET) {
            return ProgramEditorNodeCatalog.Group.TARGET;
        }
        return switch (kind.group()) {
            case TARGET -> ProgramEditorNodeCatalog.Group.TARGET;
            case COLLECTION -> ProgramEditorNodeCatalog.Group.COLLECTION;
            case FILTER -> ProgramEditorNodeCatalog.Group.FILTER;
            case MENTAL_ACTION, CONTROL_ACTION -> ProgramEditorNodeCatalog.Group.ACTION;
        };
    }
}
