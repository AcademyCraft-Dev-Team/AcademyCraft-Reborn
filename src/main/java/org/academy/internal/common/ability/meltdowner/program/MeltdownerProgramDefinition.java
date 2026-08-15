package org.academy.internal.common.ability.meltdowner.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.internal.common.ability.program.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;

import java.util.HashMap;

/** Assembles Meltdowner nodes with the shared program algebra. */
public final class MeltdownerProgramDefinition {
    private MeltdownerProgramDefinition() {
    }

    public static AbilityProgramDefinition create() {
        var category = MeltdownerProgramNodeCatalog.MELTDOWNER;
        var entryId = BaseAbilityProgramDefinition.entryId(category);
        var entryType = BaseAbilityProgramDefinition.entryType(category);
        var types = new HashMap<>(MeltdownerProgramNodeCatalog.INSTANCE.types());
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
        MeltdownerProgramNodeCatalog.INSTANCE.types().forEach((id, type) -> {
            var configuration = new JsonObject();
            if (id.equals(MeltdownerProgramNodeIds.ELECTRON_BEAM)
                    || id.equals(MeltdownerProgramNodeIds.MINING_BEAM)) {
                configuration.addProperty("power", 1.0f);
                configuration.addProperty("aim_mode", "direction");
            }
            if (id.equals(MeltdownerProgramNodeIds.ELECTRON_BEAM)) {
                configuration.addProperty("destroy_blocks", true);
            }
            if (id.equals(MeltdownerProgramNodeIds.ATOMIC_JET)) {
                configuration.addProperty("power", 1.0f);
                configuration.addProperty("destroy_blocks", true);
            }
            var suffix = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
            editor.add(
                    id,
                    type,
                    configuration,
                    id.getPath().contains("/target/")
                            ? ProgramEditorNodeCatalog.Group.TARGET
                            : ProgramEditorNodeCatalog.Group.ACTION,
                    "screen.academy.program.meltdowner.node." + suffix,
                    "screen.academy.program.port.",
                    null
            );
        });
        return new AbilityProgramDefinition(
                category,
                types,
                MeltdownerProgramExecutionBridge.categoryExecutors(),
                editor.build(),
                ProgramLimits.DEFAULT
        );
    }
}
