package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramDefinition;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramDefinition;
import org.academy.internal.common.ability.darkmatter.program.DarkmatterProgramDefinition;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramDefinition;
import org.academy.internal.common.ability.meltdowner.program.MeltdownerProgramDefinition;
import org.academy.internal.common.ability.mentalout.precision.MentaloutProgramDefinition;
import org.academy.internal.common.ability.teleport.program.TeleportProgramDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in programmable ability-category definitions.
 */
public final class AbilityProgramDefinitions {
    private static volatile Map<Identifier, AbilityProgramDefinition> DEFINITIONS = index(List.of(
            ElectromasterProgramDefinition.create(),
            TeleportProgramDefinition.create(),
            AcceleratorProgramDefinition.create(),
            MeltdownerProgramDefinition.create(),
            AeromanipProgramDefinition.create(),
            DarkmatterProgramDefinition.create(),
            MentaloutProgramDefinition.create()
    ));

    private AbilityProgramDefinitions() {
    }

    public static synchronized void includeRegisteredCategories() {
        var result = new LinkedHashMap<>(DEFINITIONS);
        for (var category : org.academy.api.common.registries.Registries.ABILITY_CATEGORIES) {
            var profile = category.getProgramProfile().orElse(null);
            if (profile == null) continue;
            if (result.containsKey(category.getKey())) {
                throw new IllegalStateException("Duplicate program profile for " + category.getKey());
            }
            var entry = org.academy.api.common.registries.Registries.PROGRAM_NODE_TYPES.get(profile.entryNode())
                    .orElseThrow(() -> new IllegalStateException("Missing entry node " + profile.entryNode().identifier())).value();
            if (!(entry instanceof org.academy.api.common.ability.program.ProgramNodeExtension<?> extension)
                    || entry.role() != org.academy.api.common.ability.program.ProgramNodeRole.ENTRY) {
                throw new IllegalStateException("Program entry must be a registered ENTRY extension: " + profile.entryNode());
            }
            var metadata = extension.editorMetadata();
            var editor = ProgramEditorNodeCatalog.builder(category.getKey()).includeCommonNodes()
                    .add(profile.entryNode().identifier(), entry, metadata.defaultConfiguration(),
                            ProgramEditorNodeCatalog.Group.FLOW, metadata.translationKey(),
                            metadata.portTranslationPrefix(), null).build();
            result.put(category.getKey(), new AbilityProgramDefinition(category.getKey(),
                    Map.of(profile.entryNode().identifier(), entry), _ -> null, editor,
                    profile.limits(), profile.spatialLimits()));
        }
        DEFINITIONS = Map.copyOf(result);
    }

    public static @Nullable AbilityProgramDefinition find(Identifier category) {
        return DEFINITIONS.get(category);
    }

    public static AbilityProgramDefinition require(Identifier category) {
        var definition = find(category);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown ability program category " + category);
        }
        return definition;
    }

    public static AbilityProgramDefinition mentalout() {
        return require(PrecisionProgramNodeCatalog.MENTALOUT);
    }

    public static Collection<AbilityProgramDefinition> all() {
        return DEFINITIONS.values();
    }

    static Map<Identifier, AbilityProgramDefinition> index(
            List<AbilityProgramDefinition> definitions
    ) {
        var result = new LinkedHashMap<Identifier, AbilityProgramDefinition>();
        for (var definition : definitions) {
            if (result.putIfAbsent(definition.category(), definition) != null) {
                throw new IllegalStateException(
                        "Duplicate ability program definition " + definition.category());
            }
        }
        return Map.copyOf(result);
    }
}
