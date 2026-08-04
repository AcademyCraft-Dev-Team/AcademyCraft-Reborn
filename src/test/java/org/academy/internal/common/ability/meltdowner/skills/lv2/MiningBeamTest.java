package org.academy.internal.common.ability.meltdowner.skills.lv2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiningBeamTest {
    @Test
    void damageUsesReferenceBaseAndPlayerScaling() {
        assertEquals(12.0f, MiningBeam.calculateDamage(1.0f, 1.0f));
        assertEquals(27.0f, MiningBeam.calculateDamage(1.5f, 1.5f));
        assertEquals(0.0f, MiningBeam.calculateDamage(-1.0f, 1.0f));
    }

    @Test
    void referenceIntervalsAndRangesRemainStable() {
        assertEquals(2, MiningBeam.CP_INTERVAL_TICKS);
        assertEquals(3, MiningBeam.BREAK_INTERVAL_TICKS);
        assertEquals(20, MiningBeam.DAMAGE_INTERVAL_TICKS);
        assertEquals(48.0f, MiningBeam.MAX_LENGTH);
        assertEquals(3, MiningBeam.MINING_TIER);
    }
}
