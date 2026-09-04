package org.academy.internal.common.ability.aeromanip.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.internal.common.ability.program.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;

import java.util.HashMap;

/**
 * Assembles Aeromanip nodes with the shared program algebra.
 */
public final class AeromanipProgramDefinition {
    private AeromanipProgramDefinition() {
    }

    public static AbilityProgramDefinition create() {
        var category = AeromanipProgramNodeCatalog.AEROMANIP;
        var entryId = BaseAbilityProgramDefinition.entryId(category);
        var entryType = BaseAbilityProgramDefinition.entryType(category);
        var types = new HashMap<>(AeromanipProgramNodeCatalog.INSTANCE.types());
        types.put(entryId, entryType);

        var editor = ProgramEditorNodeCatalog.builder(category)
                .includeCommonNodes()
                .add(
                        entryId,
                        entryType,
                        new JsonObject(),
                        ProgramEditorNodeCatalog.Group.FLOW,
                        "screen.academy.program.node.on_cast",
                        "screen.academy.program.port.",
                        null
                );
        AeromanipProgramNodeCatalog.INSTANCE.types().forEach((id, type) -> {
            var configuration = new JsonObject();
            if (id.equals(AeromanipProgramNodeIds.AIRFLOW_PUSH)
                    || id.equals(AeromanipProgramNodeIds.LAMINAR_CUT)) {
                configuration.addProperty("power", 1.0f);
            }
            if (id.equals(AeromanipProgramNodeIds.CONVERGING_AIRFLOW)) {
                configuration.addProperty("power", 1.0f);
                configuration.addProperty("maximum_targets", 6);
            }
            if (id.equals(AeromanipProgramNodeIds.LAMINAR_CUT)) {
                configuration.addProperty("charge_tier", "instant");
                configuration.addProperty("charge_acceleration", "standard");
                configuration.addProperty("plane_mode", "disabled");
            } else if (id.equals(AeromanipProgramNodeIds.PLACE_TEMPORARY_JET_NOZZLE)) {
                configuration.addProperty("target_type", "entity");
            } else if (id.equals(AeromanipProgramNodeIds.FIRE_JETS)) {
                configuration.addProperty("duration", 8);
            } else if (id.equals(AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE)) {
                configuration.addProperty("power", 1.0f);
                configuration.addProperty("radius", 2);
                configuration.addProperty("duration", 8);
                configuration.addProperty("restore_when_settled", true);
            }
            var suffix = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
            editor.add(
                    id,
                    type,
                    configuration,
                    id.getPath().contains("/target/") || id.getPath().contains("/query/")
                            ? ProgramEditorNodeCatalog.Group.TARGET
                            : ProgramEditorNodeCatalog.Group.ACTION,
                    "screen.academy.program.aeromanip.node." + suffix,
                    "screen.academy.program.port.",
                    null
            );
        });
        return new AbilityProgramDefinition(
                category,
                types,
                AeromanipProgramExecutionBridge.categoryExecutors(),
                editor.build(),
                ProgramLimits.DEFAULT
        );
    }
}
