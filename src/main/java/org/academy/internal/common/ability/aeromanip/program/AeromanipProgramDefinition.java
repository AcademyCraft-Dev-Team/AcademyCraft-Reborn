package org.academy.internal.common.ability.aeromanip.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.internal.common.ability.program.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;

import java.util.HashMap;

/** Assembles Aeromanip nodes with the shared program algebra. */
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
            var suffix = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
            editor.add(
                    id,
                    type,
                    configuration,
                    id.getPath().contains("/target/")
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
