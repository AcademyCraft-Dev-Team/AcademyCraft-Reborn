package org.academy.internal.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityConfigTest {
    @Test
    void defaultsSingleBeamAttackDelayToReferenceTiming() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        var settings = config.skills.get("single_high_speed_electron_beam");

        assertEquals(10.0f, settings.floatMap.get("attackDelayTicks"));
    }

    @Test
    void mentaloutPlayerControlDefaultsRemainBackwardCompatible() {
        var settings = AbilityConfig.Action.INSTANCE.getDefault().mentalout;

        assertTrue(settings.allowPlayerRoster);
        assertTrue(settings.allowMentalTakeover);
        assertEquals(100.0f, settings.mentalTakeoverOccupation);
        assertEquals(3.0f, settings.playerControlCostMultiplier);
        assertEquals(400, settings.playerControlResistanceTicks);
    }
}
