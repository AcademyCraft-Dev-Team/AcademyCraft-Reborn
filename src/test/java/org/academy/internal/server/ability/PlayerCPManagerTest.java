package org.academy.internal.server.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCPManagerTest {
    @Test
    void automaticallyEnablesSkillDebugModeForNamedPlayers() {
        assertTrue(PlayerCPManager.isAutomaticSkillDebugPlayer("Dev"));
        assertTrue(PlayerCPManager.isAutomaticSkillDebugPlayer("Dusk_ark"));
    }

    @Test
    void doesNotAutomaticallyEnableOtherPlayers() {
        assertFalse(PlayerCPManager.isAutomaticSkillDebugPlayer("dev"));
        assertFalse(PlayerCPManager.isAutomaticSkillDebugPlayer("Player"));
    }

    @Test
    void acceleratesCpRecoveryFivefoldInSkillDebugMode() {
        assertEquals(5, PlayerCPManager.getCpRecoveryStep(true));
        assertEquals(1, PlayerCPManager.getCpRecoveryStep(false));
    }
}
