package org.academy.internal.client.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemporalBindingRestrictionsTest {
    @Test
    void immunityBeforeOrAfterDisableRestoresOnlyOriginallyEnabledBindings() {
        var state = new TemporalBindingRestrictions();
        assertFalse(state.externalWrite("skill", false, true, false));
        assertEquals(true, state.effectiveStates(true).get("skill"));
        assertEquals(false, state.effectiveStates(false).get("skill"));
        assertTrue(state.externalWrite("skill", true, false, false));
        assertTrue(state.effectiveStates(false).isEmpty());
        assertFalse(state.externalWrite("user-disabled", false, false, true));
        assertFalse(state.externalWrite("user-disabled", true, false, true));
    }

    @Test
    void repeatedLockAndImmunityExpiryDoNotLoseTheOriginalState() {
        var state = new TemporalBindingRestrictions();
        assertTrue(state.externalWrite("skill", false, true, true));
        assertFalse(state.externalWrite("skill", false, false, false));
        assertEquals(true, state.effectiveStates(true).get("skill"));
        assertEquals(true, state.clear().get("skill"));
        assertTrue(state.effectiveStates(false).isEmpty());
    }

    @Test
    void ownerGuiOrHudWriteSupersedesLegacyExternalRequest() {
        var state = new TemporalBindingRestrictions();
        state.externalWrite("skill", false, true, true);
        state.ownedWrite("skill");
        assertTrue(state.effectiveStates(true).isEmpty());
        assertTrue(state.clear().isEmpty());
    }
}
