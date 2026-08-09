package org.academy.internal.server.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcademyCraftCommandTest {
    @Test
    void levelCommandUsesPlayableAbilityLevelRange() {
        var argumentType = AcademyCraftCommand.abilityLevelArgument();

        assertEquals(AcademyCraftCommand.MIN_COMMAND_ABILITY_LEVEL, argumentType.getMinimum());
        assertEquals(AcademyCraftCommand.MAX_COMMAND_ABILITY_LEVEL, argumentType.getMaximum());
    }
}
