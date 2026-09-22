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
    void harvestModeIndexIsClampedToTheThreeSupportedModes() {
        assertEquals(MiningBeam.HarvestMode.AUTO_SMELT,
                MiningBeam.HarvestMode.fromIndex(-1));
        assertEquals(MiningBeam.HarvestMode.FORTUNE_III,
                MiningBeam.HarvestMode.fromIndex(1));
        assertEquals(MiningBeam.HarvestMode.SILK_TOUCH,
                MiningBeam.HarvestMode.fromIndex(9));
    }
}
