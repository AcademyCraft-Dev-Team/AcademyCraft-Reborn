package org.academy.internal.common.ability.electromaster.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.internal.common.ability.program.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;

import java.util.HashMap;

/** Assembles Electromaster nodes with the shared program algebra. */
public final class ElectromasterProgramDefinition {
    private ElectromasterProgramDefinition() {
    }

    public static AbilityProgramDefinition create() {
        var category = ElectromasterProgramNodeCatalog.ELECTROMASTER;
        var entryId = BaseAbilityProgramDefinition.entryId(category);
        var entryType = BaseAbilityProgramDefinition.entryType(category);
        var types = new HashMap<>(ElectromasterProgramNodeCatalog.INSTANCE.types());
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
        ElectromasterProgramNodeCatalog.INSTANCE.types().forEach((id, type) -> {
            var configuration = new JsonObject();
            if (id.equals(ElectromasterProgramNodeIds.ARC_DISCHARGE)
                    || id.equals(ElectromasterProgramNodeIds.MAGNETIC_MOVE)) {
                configuration.addProperty("power", 1.0f);
            }
            if (id.equals(ElectromasterProgramNodeIds.MAGNETIC_MOVE)) {
                configuration.addProperty("target_type", "entity");
                configuration.addProperty("mode", "pull");
            } else if (id.equals(ElectromasterProgramNodeIds.ENERGY_DETECTION)) {
                configuration.addProperty("target_type", "entity");
                configuration.addProperty("mode", "below");
                configuration.addProperty("percent", 50.0f);
            } else if (id.equals(ElectromasterProgramNodeIds.REDSTONE_DETECTION)) {
                configuration.addProperty("mode", "below");
                configuration.addProperty("level", 8);
            } else if (id.equals(ElectromasterProgramNodeIds.CURRENT_RECHARGE)) {
                configuration.addProperty("target_type", "entity");
            }
            var suffix = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
            var group = id.getPath().contains("/target/")
                    ? ProgramEditorNodeCatalog.Group.TARGET
                    : id.getPath().contains("/logic/")
                    ? ProgramEditorNodeCatalog.Group.LOGIC
                    : ProgramEditorNodeCatalog.Group.ACTION;
            editor.add(
                    id,
                    type,
                    configuration,
                    group,
                    "screen.academy.program.electromaster.node." + suffix,
                    "screen.academy.program.port.",
                    null
            );
        });
        return new AbilityProgramDefinition(
                category,
                types,
                ElectromasterProgramExecutionBridge.categoryExecutors(),
                editor.build(),
                ProgramLimits.DEFAULT
        );
    }
}
