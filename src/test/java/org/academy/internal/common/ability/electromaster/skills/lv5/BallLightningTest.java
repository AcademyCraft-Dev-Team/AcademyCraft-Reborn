package org.academy.internal.common.ability.electromaster.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BallLightningTest {
    @Test
    void impactUsesFivePercentPlusConvertedBaseWithExistingMultipliers() {
        assertEquals(136.0f, BallLightning.Server.calculateImpactDamage(20.0f, 1.0f, 1.0f), 0.0001f);
        assertEquals(304.75f, BallLightning.Server.calculateImpactDamage(20.0f, 1.5f, 1.5f), 0.0001f);
        assertEquals(135.0f, BallLightning.Server.calculateImpactDamage(-1.0f, 1.0f, 1.0f), 0.0001f);
        assertEquals(1.0f, BallLightning.Server.calculateImpactDamage(20.0f, -1.0f, 1.0f), 0.0001f);
    }
}
