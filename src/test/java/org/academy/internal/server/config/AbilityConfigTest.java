package org.academy.internal.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityConfigTest {
    @Test
    void defaultsSingleBeamAttackDelayToReferenceTiming() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        var settings = config.skills.get("single_high_speed_electron_beam");

        assertEquals(10.0f, settings.floatMap.get("attackDelayTicks"));
    }
}
