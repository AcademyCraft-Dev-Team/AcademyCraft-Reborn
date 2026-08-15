package org.academy.internal.common.ability.darkmatter.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.internal.common.ability.program.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;

import java.util.HashMap;

/** Assembles Darkmatter nodes with the shared program algebra. */
public final class DarkmatterProgramDefinition {
    private DarkmatterProgramDefinition() {
    }

    public static AbilityProgramDefinition create() {
        var category = DarkmatterProgramNodeCatalog.DARKMATTER;
        var entryId = BaseAbilityProgramDefinition.entryId(category);
        var entryType = BaseAbilityProgramDefinition.entryType(category);
        var types = new HashMap<>(DarkmatterProgramNodeCatalog.INSTANCE.types());
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
        DarkmatterProgramNodeCatalog.INSTANCE.types().forEach((id, type) -> {
            var configuration = new JsonObject();
            if (id.equals(DarkmatterProgramNodeIds.DISASSEMBLE_BLOCK)
                    || id.equals(DarkmatterProgramNodeIds.DISASSEMBLE_ENTITY)
                    || id.equals(DarkmatterProgramNodeIds.DARKMATTER_CUT)
                    || id.equals(DarkmatterProgramNodeIds.CREATE_BEETLE)) {
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
                    "screen.academy.program.darkmatter.node." + suffix,
                    "screen.academy.program.port.",
                    null
            );
        });
        return new AbilityProgramDefinition(
                category,
                types,
                DarkmatterProgramExecutionBridge.categoryExecutors(),
                editor.build(),
                ProgramLimits.DEFAULT
        );
    }
}
