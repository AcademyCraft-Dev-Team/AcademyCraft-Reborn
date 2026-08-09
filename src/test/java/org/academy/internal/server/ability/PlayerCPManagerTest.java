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

    @Test
    void smallCpRecoveriesAccumulateBeforeSpIsConsumed() {
        var first = PlayerCPManager.planCpRecovery(4.0f, 0.0f, 10);
        assertEquals(4.0f, first.recoveredCp(), 0.0001f);
        assertEquals(4.0f, first.remainderCp(), 0.0001f);
        assertEquals(0, first.spCost());

        var second = PlayerCPManager.planCpRecovery(5.0f, first.remainderCp(), 10);
        assertEquals(5.0f, second.recoveredCp(), 0.0001f);
        assertEquals(9.0f, second.remainderCp(), 0.0001f);
        assertEquals(0, second.spCost());

        var third = PlayerCPManager.planCpRecovery(1.0f, second.remainderCp(), 10);
        assertEquals(1.0f, third.recoveredCp(), 0.0001f);
        assertEquals(0.0f, third.remainderCp(), 0.0001f);
        assertEquals(1, third.spCost());
    }

    @Test
    void finalSpOnlyRecoversCpUpToTheNextTenPointBoundary() {
        var plan = PlayerCPManager.planCpRecovery(20.0f, 8.0f, 1);
        assertEquals(2.0f, plan.recoveredCp(), 0.0001f);
        assertEquals(0.0f, plan.remainderCp(), 0.0001f);
        assertEquals(1, plan.spCost());
    }

    @Test
    void cpIterationRecoveryStopsAtZeroSp() {
        var plan = PlayerCPManager.planCpRecovery(0.25f, 9.0f, 0);
        assertEquals(0.0f, plan.recoveredCp(), 0.0001f);
        assertEquals(9.0f, plan.remainderCp(), 0.0001f);
        assertEquals(0, plan.spCost());
    }

    @Test
    void debugMaximumCpOverridesTheNaturallyCalculatedMaximum() {
        assertEquals(250.0f, PlayerCPManager.resolveEffectiveMaxCP(640.0f, 250.0f));
        assertEquals(640.0f, PlayerCPManager.resolveEffectiveMaxCP(640.0f, null));
        assertEquals(0.0f, PlayerCPManager.resolveEffectiveMaxCP(640.0f, Float.NaN));
    }
}
