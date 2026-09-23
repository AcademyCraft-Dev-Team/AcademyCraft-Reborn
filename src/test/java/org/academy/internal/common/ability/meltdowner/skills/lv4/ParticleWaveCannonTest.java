package org.academy.internal.common.ability.meltdowner.skills.lv4;

import org.academy.internal.common.ability.meltdowner.beam.MeltdownerBeamDamage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticleWaveCannonTest {
    @Test
    void damageUsesReferenceHealthFormulaAndRadiationMultiplier() {
        assertEquals(121.0f, MeltdownerBeamDamage.calculatePowerScaledBase(
                ParticleWaveCannon.BASE_DAMAGE,
                ParticleWaveCannon.MAX_HEALTH_DAMAGE_RATIO,
                100.0f,
                2.0f,
                1.5f,
                false,
                1.5f
        ));
        assertEquals(181.0f, MeltdownerBeamDamage.calculatePowerScaledBase(
                ParticleWaveCannon.BASE_DAMAGE,
                ParticleWaveCannon.MAX_HEALTH_DAMAGE_RATIO,
                100.0f,
                2.0f,
                1.5f,
                true,
                1.5f
        ));
    }

}
