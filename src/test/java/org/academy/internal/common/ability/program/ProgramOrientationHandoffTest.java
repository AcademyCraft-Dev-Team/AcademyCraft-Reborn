package org.academy.internal.common.ability.program;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramOrientationHandoffTest {
    @Test
    void solverCompletionKeepsTheExactLeaseForVmPollingAndItsImmediateSuccessor() {
        var token = UUID.randomUUID();
        var handoff = new ProgramOrientationHandoff(token);
        assertFalse(handoff.retain(199, token, true, true, false), "an ordinary idle lease has no angular extension");
        assertTrue(handoff.retain(280, token, true, true, true));
        assertTrue(handoff.retain(281, token, true, true, false), "solver has completed before the VM polls");
        assertTrue(handoff.retain(282, token, true, true, false), "downstream flow can use the still-owned lease");
        assertTrue(handoff.retain(283, token, true, true, true), "successor command owns its own bounded active phase");
        assertTrue(handoff.retain(303, token, true, true, false));
        assertFalse(handoff.retain(304, token, true, true, false));
    }

    @Test
    void inactivePollingCannotKeepAnAbandonedReceiptOrLeaseAlive() {
        var token = UUID.randomUUID();
        var handoff = new ProgramOrientationHandoff(token);
        assertTrue(handoff.retain(1_000, token, true, true, true));
        for (int tick = 1_001; tick <= 1_020; tick++) assertTrue(handoff.retain(tick, token, true, true, false));
        for (int tick = 1_021; tick <= 1_050; tick++) assertFalse(handoff.retain(tick, token, true, true, false));
    }

    @Test
    void revokedOrReplacedTokensNeverRegainAuthorityFromTheCompletionWindow() {
        var token = UUID.randomUUID();
        var handoff = new ProgramOrientationHandoff(token);
        assertTrue(handoff.retain(10, token, true, true, true));
        assertFalse(handoff.retain(11, token, true, false, true), "an active receipt cannot revive a revoked lease");
        assertFalse(handoff.retain(12, token, true, true, false), "revocation discarded the old window");
        assertTrue(handoff.retain(13, token, true, true, true));
        assertFalse(handoff.retain(14, UUID.randomUUID(), true, true, true), "a successor token cannot use this session");
        assertFalse(handoff.retain(15, token, true, true, false));
    }

    @Test
    void manualHoverNeverReceivesOrInheritsAProgramExtension() {
        var token = UUID.randomUUID();
        var handoff = new ProgramOrientationHandoff(token);
        assertFalse(handoff.retain(200, token, false, true, true));
        assertTrue(handoff.retain(210, token, true, true, true));
        assertFalse(handoff.retain(211, token, false, true, false), "manual H clears program-only handoff state");
        assertFalse(handoff.retain(212, token, true, true, false), "returning to program mode cannot revive the old window");
    }
}
