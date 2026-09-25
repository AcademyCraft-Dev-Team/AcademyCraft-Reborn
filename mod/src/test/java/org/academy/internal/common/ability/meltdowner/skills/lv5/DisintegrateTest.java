package org.academy.internal.common.ability.meltdowner.skills.lv5;

import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisintegrateTest {
    @Test
    void everyTargetUsesFivePercentPlusConvertedBaseAndPlayerScaling() {
        assertEquals(76.0f, Disintegrate.Server.calculateDamage(20.0f, 1.0f), 0.0001f);
        assertEquals(113.5f, Disintegrate.Server.calculateDamage(20.0f, 1.5f), 0.0001f);
        assertEquals(75.0f, Disintegrate.Server.calculateDamage(-1.0f, 1.0f), 0.0001f);
        assertEquals(575.0f, Disintegrate.Server.calculateDamage(10000.0f, 1.0f), 0.0001f);
    }

    @Test
    void spawnedBeamProfilePreservesRadiationAndPlayerMultipliersOnConvertedBase() {
        var profile = Disintegrate.Server.DAMAGE;
        assertEquals(75.0f, profile.baseDamage(), 0.0001f);
        assertEquals(0.05f, profile.maxHealthRatio());
        assertEquals(155.0f, MeltdownerBeamDamage.calculate(
                profile.baseDamage(), profile.maxHealthRatio(), 100.0f, 2.0f, false), 0.0001f);
        assertEquals(230.0f, MeltdownerBeamDamage.calculate(
                profile.baseDamage(), profile.maxHealthRatio(), 100.0f, 2.0f, true, 1.5f), 0.0001f);
    }
}
