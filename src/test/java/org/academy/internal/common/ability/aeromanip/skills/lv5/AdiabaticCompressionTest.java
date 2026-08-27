package org.academy.internal.common.ability.aeromanip.skills.lv5;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdiabaticCompressionTest {
    @Test
    void milestoneTwoExpandsTheCompressionRadius() {
        assertEquals(AdiabaticCompression.BASE_RADIUS,
                AdiabaticCompression.radiusForMilestone(0));
        assertEquals(AdiabaticCompression.MILESTONE_TWO_RADIUS,
                AdiabaticCompression.radiusForMilestone(2));
    }

    @Test
    void movementModifierLeavesExactlyFivePercent() {
        assertEquals(0.05, 1.0 + AdiabaticCompression.movementModifierAmount(), 1.0e-9);
    }

    @Test
    void damageStacksGrowWithoutAnOrdinaryGameplayCap() {
        assertEquals(1, AdiabaticCompression.nextStackCount(0));
        assertEquals(100_001, AdiabaticCompression.nextStackCount(100_000));
        assertEquals(Integer.MAX_VALUE,
                AdiabaticCompression.nextStackCount(Integer.MAX_VALUE));
    }

    @Test
    void milestoneThreeIncreasesEveryStacksDamage() {
        assertEquals(5.0f, AdiabaticCompression.damageForStacks(10, 0.5f, 0));
        assertEquals(6.25f, AdiabaticCompression.damageForStacks(10, 0.5f, 3));
    }

    @Test
    void sphericalAreaIncludesItsBoundary() {
        var center = new Vec3(1.0, 2.0, 3.0);
        assertTrue(AdiabaticCompression.contains(center, center.add(6.0, 0.0, 0.0), 6.0));
        assertFalse(AdiabaticCompression.contains(center, center.add(6.01, 0.0, 0.0), 6.0));
    }
}
