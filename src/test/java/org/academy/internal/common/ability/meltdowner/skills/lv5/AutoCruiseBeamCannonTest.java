package org.academy.internal.common.ability.meltdowner.skills.lv5;

import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoCruiseBeamCannonTest {
    @Test
    void delayedShotUsesReferenceDamageFormula() {
        assertEquals(11.0f, MeltdownerBeamDamage.calculate(
                AutoCruiseBeamCannon.BASE_DAMAGE,
                AutoCruiseBeamCannon.MAX_HEALTH_DAMAGE_RATIO,
                100.0f,
                1.0f,
                false
        ));
        assertEquals(16.5f, MeltdownerBeamDamage.calculate(
                AutoCruiseBeamCannon.BASE_DAMAGE,
                AutoCruiseBeamCannon.MAX_HEALTH_DAMAGE_RATIO,
                100.0f,
                1.0f,
                true
        ));
    }

    @Test
    void scanFireAndVisualDelayRemainBounded() {
        assertEquals(10, AutoCruiseBeamCannon.DETECT_INTERVAL_TICKS);
        assertEquals(2, AutoCruiseBeamCannon.FIRE_INTERVAL_TICKS);
        assertEquals(40, AutoCruiseBeamCannon.DAMAGE_DELAY_TICKS);
        assertEquals(16.0, AutoCruiseBeamCannon.SCAN_RADIUS);
    }
}
