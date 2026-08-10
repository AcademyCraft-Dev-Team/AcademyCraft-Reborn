package org.academy.internal.common.ability.meltdowner.skills.lv4;

import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticleWaveCannonTest {
    @Test
    void damageUsesReferenceHealthFormulaAndRadiationMultiplier() {
        assertEquals(41.0f, MeltdownerBeamDamage.calculate(
                ParticleWaveCannon.BASE_DAMAGE,
                ParticleWaveCannon.MAX_HEALTH_DAMAGE_RATIO,
                100.0f,
                1.0f,
                false
        ));
        assertEquals(61.0f, MeltdownerBeamDamage.calculate(
                ParticleWaveCannon.BASE_DAMAGE,
                ParticleWaveCannon.MAX_HEALTH_DAMAGE_RATIO,
                100.0f,
                1.0f,
                true
        ));
    }

    @Test
    void chargeAndBeamBoundsMatchReference() {
        assertEquals(25, ParticleWaveCannon.CHARGE_TICKS);
        assertEquals(2, ParticleWaveCannon.CP_INTERVAL_TICKS);
        assertEquals(10, ParticleWaveCannon.DAMAGE_INTERVAL_TICKS);
        assertEquals(85.0f, ParticleWaveCannon.MAX_LENGTH);
        assertEquals(3, ParticleWaveCannon.MINING_TIER);
    }
}
