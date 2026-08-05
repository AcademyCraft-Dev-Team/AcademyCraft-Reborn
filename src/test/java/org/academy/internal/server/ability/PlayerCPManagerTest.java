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
        assertEquals(5, PlayerCPManager.getCpIterationRate(true));
        assertEquals(1, PlayerCPManager.getCpIterationRate(false));
    }

    @Test
    void atomicReplacementCanReuseTheExistingPermanentReservation() {
        assertTrue(PlayerCPManager.isAtomicReplacementAffordable(10.0f, 50.0f, 60.0f));
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(10.0f, 50.0f, 60.01f));
    }

    @Test
    void atomicReplacementRejectsInvalidAmounts() {
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(Float.NaN, 0.0f, 0.0f));
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(10.0f, -1.0f, 5.0f));
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(10.0f, 0.0f, Float.POSITIVE_INFINITY));
    }

    @Test
    void atomicReplacementCanShrinkReservationsWhileAvailableCpIsNegative() {
        assertTrue(PlayerCPManager.isAtomicReplacementAffordable(-25.0f, 60.0f, 30.0f));
        assertTrue(PlayerCPManager.isAtomicReplacementAffordable(-25.0f, 60.0f, 0.0f));
        assertFalse(PlayerCPManager.isAtomicReplacementAffordable(-25.0f, 20.0f, 30.0f));
    }
}
