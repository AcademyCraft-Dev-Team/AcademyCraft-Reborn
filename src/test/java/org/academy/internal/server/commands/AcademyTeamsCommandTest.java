package org.academy.internal.server.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class AcademyTeamsCommandTest {
    @Test
    void selfServiceTeamsDoNotRequireOperatorPermission() {
        assertTrue(AcademyTeamsCommand.canUse(null));
    }
}
