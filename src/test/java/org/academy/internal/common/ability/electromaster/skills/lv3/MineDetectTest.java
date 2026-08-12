package org.academy.internal.common.ability.electromaster.skills.lv3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineDetectTest {
    @Test
    void scanUsesAClosedSphere() {
        assertTrue(MineDetect.isInsideScanRadius(64, 0, 0));
        assertTrue(MineDetect.isInsideScanRadius(32, 32, 32));
        assertFalse(MineDetect.isInsideScanRadius(64, 1, 0));
    }

    @Test
    void fallbackRecognizesCommonOreNamingPatterns() {
        assertTrue(MineDetect.isOrePath("deepslate_iron_ore"));
        assertTrue(MineDetect.isOrePath("ore_copper"));
        assertTrue(MineDetect.isOrePath("rich_ore_cluster"));
        assertFalse(MineDetect.isOrePath("oreganized_crystal"));
    }
}
