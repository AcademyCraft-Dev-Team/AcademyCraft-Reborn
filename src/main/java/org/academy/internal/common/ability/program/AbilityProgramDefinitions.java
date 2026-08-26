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
    private static final Map<Identifier, AbilityProgramDefinition> DEFINITIONS = index(List.of(
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
