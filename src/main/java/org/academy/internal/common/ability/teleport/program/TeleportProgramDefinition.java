package org.academy.internal.common.ability.teleport.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.internal.common.ability.program.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;

import java.util.HashMap;

/** Assembles Teleport nodes with the shared program algebra. */
public final class TeleportProgramDefinition {
    private TeleportProgramDefinition() {
    }

    public static AbilityProgramDefinition create() {
        var category = TeleportProgramNodeCatalog.TELEPORT;
        var entryId = BaseAbilityProgramDefinition.entryId(category);
        var entryType = BaseAbilityProgramDefinition.entryType(category);
        var types = new HashMap<>(TeleportProgramNodeCatalog.INSTANCE.types());
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
        TeleportProgramNodeCatalog.INSTANCE.types().forEach((id, type) -> {
            var configuration = new JsonObject();
            if (id.equals(TeleportProgramNodeIds.SELF_TELEPORT)
                    || id.equals(TeleportProgramNodeIds.ENTITY_TELEPORT)) {
                configuration.addProperty("power", 1.0f);
            }
            if (id.equals(TeleportProgramNodeIds.ENTITY_TELEPORT)) {
                configuration.addProperty("target_type", "entity");
            }
            if (id.equals(TeleportProgramNodeIds.BLOCK_ITEM_TELEPORT)) {
                configuration.addProperty("mode", "place");
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
                    "screen.academy.program.teleport.node." + suffix,
                    "screen.academy.program.port.",
                    null
            );
        });
        return new AbilityProgramDefinition(
                category,
                types,
                TeleportProgramExecutionBridge.categoryExecutors(),
                editor.build(),
                ProgramLimits.DEFAULT
        );
    }
}
