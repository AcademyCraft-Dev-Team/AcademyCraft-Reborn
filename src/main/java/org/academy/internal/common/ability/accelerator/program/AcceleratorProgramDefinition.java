package org.academy.internal.common.ability.accelerator.program;

import com.google.gson.JsonObject;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
import org.academy.internal.common.ability.program.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;

import java.util.HashMap;

/**
 * Assembles vector-manipulation nodes with the shared program algebra.
 */
public final class AcceleratorProgramDefinition {
    private AcceleratorProgramDefinition() {
    }

    public static AbilityProgramDefinition create() {
        var category = AcceleratorProgramNodeCatalog.ACCELERATOR;
        var entryId = BaseAbilityProgramDefinition.entryId(category);
        var entryType = BaseAbilityProgramDefinition.entryType(category);
        var types = new HashMap<>(AcceleratorProgramNodeCatalog.INSTANCE.types());
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
        AcceleratorProgramNodeCatalog.INSTANCE.types().forEach((id, type) -> {
            var configuration = new JsonObject();
            if (id.equals(AcceleratorProgramNodeIds.APPLY_VECTOR)
                    || id.equals(AcceleratorProgramNodeIds.KINETIC_IMPACT)
                    || id.equals(AcceleratorProgramNodeIds.DISPLACE_ENTITY)
                    || id.equals(AcceleratorProgramNodeIds.DISPLACE_BLOCK)) {
                configuration.addProperty(
                        "strength", AcceleratorProgramStrength.STANDARD.wireId());
            } else if (id.equals(AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE)) {
                configuration.addProperty("power", 1.0f);
                configuration.addProperty("destroy_blocks", false);
                configuration.addProperty("radius", KineticEnergyApplied.DEFAULT_PROGRAM_RADIUS);
            }
            var suffix = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
            editor.add(
                    id,
                    type,
                    configuration,
                    id.getPath().contains("/target/")
                            ? ProgramEditorNodeCatalog.Group.TARGET
                            : ProgramEditorNodeCatalog.Group.ACTION,
                    "screen.academy.program.accelerator.node." + suffix,
                    "screen.academy.program.port.",
                    null
            );
        });
        return new AbilityProgramDefinition(
                category,
                types,
                AcceleratorProgramExecutionBridge.categoryExecutors(),
                editor.build(),
                ProgramLimits.DEFAULT
        );
    }
}
