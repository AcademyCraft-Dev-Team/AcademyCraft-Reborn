package org.academy.api.common.util;

import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelUtilTest {
    @Test
    void abilityInteractionShapeFallsBackToOutlineWhenCollisionIsEmpty() {
        var outline = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.125, 1.0);
        var selected = LevelUtil.selectAbilityInteractionShape(Shapes.empty(), outline);

        assertFalse(selected.isEmpty());
        assertEquals(0.125, selected.bounds().maxY, 1.0E-8);
    }

    @Test
    void miningLevelsAtOrAboveDiamondAreUnrestricted() {
        assertTrue(LevelUtil.isUnrestrictedMiningLevel(3));
        assertTrue(LevelUtil.isUnrestrictedMiningLevel(4));
        assertFalse(LevelUtil.isUnrestrictedMiningLevel(2));
        assertFalse(LevelUtil.isUnrestrictedMiningLevel(-1));
    }
}
