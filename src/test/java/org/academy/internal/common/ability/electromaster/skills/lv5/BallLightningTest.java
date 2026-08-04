package org.academy.internal.common.ability.electromaster.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BallLightningTest {
    @Test
    void impactReplacesDirectHealthWritesWithEquivalentAttributedDamage() {
        assertEquals(16.0f, BallLightning.Server.calculateImpactDamage(20.0f, 1.0f, 1.0f), 0.0001f);
        assertEquals(36.0f, BallLightning.Server.calculateImpactDamage(20.0f, 1.5f, 1.5f), 0.0001f);
        assertEquals(10.0f, BallLightning.Server.calculateImpactDamage(-1.0f, 1.0f, 1.0f), 0.0001f);
        assertEquals(0.0f, BallLightning.Server.calculateImpactDamage(20.0f, -1.0f, 1.0f), 0.0001f);
    }
}
