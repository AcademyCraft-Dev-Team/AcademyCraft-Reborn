package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityProgramSpatialRangesTest {
    @Test
    void builtInCategoriesUseOneDifferentialForQueriesAndActions() {
        var expected = Map.of(
                AbilityCategoryNames.TELEPORT, 64.0,
                AbilityCategoryNames.MENTALOUT, 64.0,
                AbilityCategoryNames.DARKMATTER, 48.0,
                AbilityCategoryNames.ELECTROMASTER, 48.0,
                AbilityCategoryNames.MELTDOWNER, 32.0,
                AbilityCategoryNames.AEROMANIP, 32.0,
                AbilityCategoryNames.ACCELERATOR, 24.0
        );

        expected.forEach((category, range) -> {
            var limits = AbilityProgramSpatialRanges.forCategory(AcademyCraft.academy(category));
            assertEquals(range, limits.queryRange(), category);
            assertEquals(range, limits.actionRange(), category);
        });
    }

    @Test
    void extensionCategoriesUseTheThirtyTwoBlockDefault() {
        var limits = AbilityProgramSpatialRanges.forCategory(
                Identifier.parse("extension:custom_ability"));

        assertEquals(32.0, limits.queryRange());
        assertEquals(32.0, limits.actionRange());
        assertEquals(32.0, AbilityProgramSpatialRanges.forCategory(
                Identifier.parse("extension:teleport")).actionRange());
    }

    @Test
    void abilityDefinitionsPublishTheirCentralSpatialLimits() {
        for (var definition : AbilityProgramDefinitions.all()) {
            assertEquals(
                    AbilityProgramSpatialRanges.forCategory(definition.category()),
                    definition.spatialLimits(),
                    definition.category().toString()
            );
        }
    }
}
