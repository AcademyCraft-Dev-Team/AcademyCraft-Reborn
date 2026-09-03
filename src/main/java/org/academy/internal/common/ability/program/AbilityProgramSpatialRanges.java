package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramSpatialLimits;
import org.academy.internal.common.ability.AbilityCategoryNames;

import java.util.Map;
import java.util.Objects;

/**
 * Central range policy for built-in precision-operation categories.
 */
public final class AbilityProgramSpatialRanges {
    private static final Map<Identifier, ProgramSpatialLimits> BUILT_IN = Map.of(
            AcademyCraft.academy(AbilityCategoryNames.TELEPORT), ProgramSpatialLimits.uniform(64.0),
            AcademyCraft.academy(AbilityCategoryNames.MENTALOUT), ProgramSpatialLimits.uniform(64.0),
            AcademyCraft.academy(AbilityCategoryNames.DARKMATTER), ProgramSpatialLimits.uniform(48.0),
            AcademyCraft.academy(AbilityCategoryNames.ELECTROMASTER), ProgramSpatialLimits.uniform(48.0),
            AcademyCraft.academy(AbilityCategoryNames.MELTDOWNER), ProgramSpatialLimits.uniform(32.0),
            AcademyCraft.academy(AbilityCategoryNames.AEROMANIP), ProgramSpatialLimits.uniform(32.0),
            AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR), ProgramSpatialLimits.uniform(24.0)
    );

    private AbilityProgramSpatialRanges() {
    }

    /**
     * Returns the built-in differential or the shared 32-block default for extension categories.
     */
    public static ProgramSpatialLimits forCategory(Identifier category) {
        Objects.requireNonNull(category, "category");
        return BUILT_IN.getOrDefault(category, ProgramSpatialLimits.DEFAULT);
    }
}
