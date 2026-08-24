package org.academy.internal.server.commands;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademyCraftCommandTest {
    @Test
    void levelCommandUsesPlayableAbilityLevelRange() {
        var argumentType = AcademyCraftCommand.abilityLevelArgument();

        assertEquals(AcademyCraftCommand.MIN_COMMAND_ABILITY_LEVEL, argumentType.getMinimum());
        assertEquals(AcademyCraftCommand.MAX_COMMAND_ABILITY_LEVEL, argumentType.getMaximum());
    }

    @Test
    void nonOperatorsCanOnlyResetTheirCategoryToLevelZero() {
        assertTrue(AcademyCraftCommand.canSetAbilityCategory(
                AcademyCraft.academy("level0"), false));
        assertFalse(AcademyCraftCommand.canSetAbilityCategory(
                AcademyCraft.academy("darkmatter"), false));
        assertFalse(AcademyCraftCommand.canSetAbilityCategory(
                Identifier.withDefaultNamespace("level0"), false));
    }

    @Test
    void operatorsCanSetEveryRegisteredNamespace() {
        assertTrue(AcademyCraftCommand.canSetAbilityCategory(
                AcademyCraft.academy("darkmatter"), true));
        assertTrue(AcademyCraftCommand.canSetAbilityCategory(
                Identifier.fromNamespaceAndPath("example", "custom"), true));
    }

    @Test
    void propsResetIsRegisteredWithoutAnOperatorRequirement() {
        var props = AcademyCraftCommand.propsCommands().build();
        var reset = props.getChild("reset");
        assertNotNull(reset);
        assertTrue(props.getRequirement().test(null));
        assertTrue(reset.getRequirement().test(null));
    }
}
