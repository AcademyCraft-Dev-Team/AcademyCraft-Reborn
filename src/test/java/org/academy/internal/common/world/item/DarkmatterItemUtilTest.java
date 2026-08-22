package org.academy.internal.common.world.item;

import org.academy.internal.common.ability.darkmatter.DarkmatterIntegrityCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkmatterItemUtilTest {
    @Test
    void passiveDecayReachesZeroOnExactlyTheConfiguredLifetime() {
        var integrity = 1.0f;
        for (var tick = 0; tick < 11_999; tick++) {
            integrity = DarkmatterIntegrityCurve.nextPassiveIntegrity(integrity, 12_000);
        }
        assertTrue(integrity > 0.0f);
        assertEquals(0.0f,
                DarkmatterIntegrityCurve.nextPassiveIntegrity(integrity, 12_000), 0.0f);
    }
}
