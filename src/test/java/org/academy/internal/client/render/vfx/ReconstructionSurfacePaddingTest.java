package org.academy.internal.client.render.vfx;

import org.academy.api.common.vfx.RepairVisualParts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReconstructionSurfacePaddingTest {
    @Test void uncoveredPlayerStillClearsSkinOverlaysAndTheLargerHatLayer() {
        for (int part : new int[]{0, 2, 3, 4, 5}) {
            assertTrue(ReconstructionSurfacePadding.forPart(part, 0, false) > .25f / 16);
        }
        assertTrue(ReconstructionSurfacePadding.forPart(1, 0, false) > .5f / 16);
        assertTrue(ReconstructionSurfacePadding.forPart(1, 0, false)
                > ReconstructionSurfacePadding.forPart(0, 0, false));
    }

    @Test void chestArmorCoversTheTorsoAndBothArmsWithoutInflatingUnrelatedParts() {
        for (int part : new int[]{0, 2, 3})
            assertEquals(ReconstructionSurfacePadding.OUTER_ARMOR,
                    ReconstructionSurfacePadding.forPart(part, RepairVisualParts.CHEST, false));
        assertEquals(ReconstructionSurfacePadding.HAT,
                ReconstructionSurfacePadding.forPart(1, RepairVisualParts.CHEST, false));
        assertEquals(ReconstructionSurfacePadding.SKIN,
                ReconstructionSurfacePadding.forPart(4, RepairVisualParts.CHEST, false));
    }

    @Test void leggingsAlsoCoverTheTorsoButNotArmsAndBootPaddingIsLocalToFeet() {
        int armor = RepairVisualParts.LEGS | RepairVisualParts.FEET;
        assertEquals(ReconstructionSurfacePadding.INNER_ARMOR, ReconstructionSurfacePadding.forPart(0, armor, false));
        assertEquals(ReconstructionSurfacePadding.SKIN, ReconstructionSurfacePadding.forPart(2, armor, false));
        for (int part : new int[]{4, 5}) {
            assertEquals(ReconstructionSurfacePadding.INNER_ARMOR, ReconstructionSurfacePadding.forPart(part, armor, false));
            assertEquals(ReconstructionSurfacePadding.OUTER_ARMOR, ReconstructionSurfacePadding.forPart(part, armor, true));
            assertEquals(ReconstructionSurfacePadding.SKIN,
                    ReconstructionSurfacePadding.forPart(part, RepairVisualParts.FEET, false));
        }
    }

    @Test void equipmentPaddingClearsActualStandardArmorDeformation() {
        assertTrue(ReconstructionSurfacePadding.OUTER_ARMOR > 1f / 16);
        assertTrue(ReconstructionSurfacePadding.INNER_ARMOR > .5f / 16);
        assertEquals(ReconstructionSurfacePadding.OUTER_ARMOR,
                ReconstructionSurfacePadding.forPart(1, RepairVisualParts.HEAD, false));
    }
}
