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
            var suffix = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
            editor.add(
                    id,
                    type,
                    configuration,
                    id.getPath().contains("/target/")
                            ? ProgramEditorNodeCatalog.Group.TARGET
                            : ProgramEditorNodeCatalog.Group.ACTION,
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
