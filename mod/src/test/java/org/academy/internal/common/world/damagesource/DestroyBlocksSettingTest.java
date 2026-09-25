package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestroyBlocksSettingTest {
    @Test
    void miningBeamUsesOnlyItsAdvancedBlockDestructionSetting() {
        assertTrue(DestroyBlocksSetting.usesIndependentBlockDestructionSetting(
                "academy:mining_beam"));
        assertFalse(DestroyBlocksSetting.usesIndependentBlockDestructionSetting(
                "academy:particle_wave_cannon"));
        assertFalse(DestroyBlocksSetting.usesIndependentBlockDestructionSetting((String) null));
    }
}
