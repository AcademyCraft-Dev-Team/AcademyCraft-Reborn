package org.academy.internal.client.ability.mentalout;

import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModularProgramScreenPaletteTest {
    @Test
    void everyAbilityCategoryUsesItsOwnRequestedAccent() {
        var categories = List.of(
                AbilityCategoryNames.ACCELERATOR,
                AbilityCategoryNames.MELTDOWNER,
                AbilityCategoryNames.DARKMATTER,
                AbilityCategoryNames.AEROMANIP,
                AbilityCategoryNames.ELECTROMASTER,
                AbilityCategoryNames.MENTALOUT,
                AbilityCategoryNames.TELEPORT
        );
        var colors = new HashSet<Integer>();
        for (var category : categories) {
            colors.add(ModularProgramScreen.categoryAccent(AcademyCraft.academy(category)));
        }
        assertEquals(categories.size(), colors.size());
    }

    @Test
    void compatibleEntityFamiliesShareColorsAndSemanticTypesRemainDistinct() {
        assertEquals(
                ModularProgramScreen.portColor(ProgramValueTypes.ENTITY_REFERENCE),
                ModularProgramScreen.portColor(ProgramValueTypes.LIVING_ENTITY_REFERENCE)
        );
        assertEquals(
                ModularProgramScreen.portColor(ProgramValueTypes.ENTITY_SET),
                ModularProgramScreen.portColor(ProgramValueTypes.LIVING_ENTITY_SET)
        );
        var colors = new HashSet<Integer>();
        for (var type : List.of(
                ProgramValueTypes.FLOW,
                ProgramValueTypes.BOOLEAN,
                ProgramValueTypes.INTEGER,
                ProgramValueTypes.BIG_INTEGER,
                ProgramValueTypes.FLOAT,
                ProgramValueTypes.DIRECTION,
                ProgramValueTypes.WORLD_POSITION,
                ProgramValueTypes.BLOCK_POSITION,
                ProgramValueTypes.ENTITY_REFERENCE
        )) {
            colors.add(ModularProgramScreen.portColor(type));
        }
        assertTrue(colors.size() >= 8);
    }

    @Test
    void longConfigurationSignaturesRequestAFullValueAndExpandTheInspectorRow() {
        assertTrue(ModularProgramScreen.configurationOptionNeedsTooltip(180.0f, 60));
        assertFalse(ModularProgramScreen.configurationOptionNeedsTooltip(40.0f, 200));
        assertFalse(ModularProgramScreen.configurationOptionNeedsTooltip(0.0f, 60));
        assertTrue(ModularProgramScreen.expandedConfigurationRowHeight(2)
                > ModularProgramScreen.expandedConfigurationRowHeight(0));
        assertTrue(ModularProgramScreen.expandedConfigurationRowHeight(4)
                > ModularProgramScreen.expandedConfigurationRowHeight(2));
    }
}
