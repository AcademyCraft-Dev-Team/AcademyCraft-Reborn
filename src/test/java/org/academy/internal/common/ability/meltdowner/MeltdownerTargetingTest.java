package org.academy.internal.common.ability.meltdowner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeltdownerTargetingTest {
    @Test
    void friendlyFireAllowsAlliedPlayers() {
        assertTrue(MeltdownerTargeting.allowsTarget(true, true, true));
    }

    @Test
    void disabledFriendlyFireProtectsAlliedPlayers() {
        assertFalse(MeltdownerTargeting.allowsTarget(true, true, false));
    }

    @Test
    void nonAlliedTargetsRemainAttackable() {
        assertTrue(MeltdownerTargeting.allowsTarget(false, true, false));
        assertTrue(MeltdownerTargeting.allowsTarget(false, false, false));
    }

    @Test
    void alliedNonPlayersKeepTheirExistingProtection() {
        assertFalse(MeltdownerTargeting.allowsTarget(true, false, true));
    }
}
