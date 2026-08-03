package org.academy.internal.common.ability.meltdowner.skills.lv3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BetaParticleStreamTest {
    @Test
    void serverChargeCountAndDamageAreBounded() {
        assertEquals(1, BetaParticleStream.Server.getCharges(100, 90));
        assertEquals(2, BetaParticleStream.Server.getCharges(100, 130));
        assertEquals(5, BetaParticleStream.Server.getCharges(100, 1000));
        assertEquals(6.0f, BetaParticleStream.Server.calculateDamage(1.0f), 0.0001f);
        assertEquals(9.0f, BetaParticleStream.Server.calculateDamage(1.5f), 0.0001f);
    }
}
