package org.academy.internal.common.ability.darkmatter.skills.lv2;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterCutTest {
    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);

    @Test
    void acceptsTargetsInsideForwardCone() {
        assertTrue(DarkmatterCut.Server.insideCone(ORIGIN, FORWARD,
                new Vec3(2, 0, 6),
                DarkmatterCut.Server.effectiveRadius(3, 0, false, 0),
                DarkmatterCut.Server.effectiveMinimumDot(3, 0, false)));
    }

    @Test
    void rejectsTargetsBehindOrOutsideRange() {
        assertFalse(DarkmatterCut.Server.insideCone(ORIGIN, FORWARD,
                new Vec3(0, 0, -3), DarkmatterCut.RADIUS, DarkmatterCut.MIN_DOT));
        assertFalse(DarkmatterCut.Server.insideCone(ORIGIN, FORWARD,
                new Vec3(0, 0, 9), DarkmatterCut.RADIUS, DarkmatterCut.MIN_DOT));
    }

    @Test
    void secondMilestoneExpandsDistanceAndCone() {
        var baseRadius = DarkmatterCut.Server.effectiveRadius(1.0f, 1, false, 0);
        var upgradedRadius = DarkmatterCut.Server.effectiveRadius(1.0f, 2, false, 0);
        assertEquals(baseRadius * 1.15, upgradedRadius, 0.0001);
        assertTrue(DarkmatterCut.Server.effectiveMinimumDot(1.0f, 2, false)
                < DarkmatterCut.Server.effectiveMinimumDot(1.0f, 1, false));
    }

    @Test
    void sixWingsAreaBonusStacksWithCutMilestone() {
        assertEquals(7.27375, DarkmatterCut.Server.effectiveRadius(
                0.5f, 2, true, 2), 0.0001);
    }

    @Test
    void gammaAutomaticallyControlsMirrorCut() {
        assertEquals(0.0f, DarkmatterCut.Server.delayedRiftDamageMultiplier(0.0f, 3), 0.0001f);
        assertEquals(0.35f, DarkmatterCut.Server.delayedRiftDamageMultiplier(1.0f, 2), 0.0001f);
        assertEquals(0.5f, DarkmatterCut.Server.delayedRiftDamageMultiplier(3), 0.0001f);
        assertEquals(105, DarkmatterCut.Server.markDuration(1.5f, 2));
    }

    @Test
    void alphaAndBetaProduceDistinctCombatNumbers() {
        assertEquals(8.0f, DarkmatterCut.Server.directDamage(1.0f, 0.0f), 0.0001f);
        assertEquals(12.0f, DarkmatterCut.Server.directDamage(3.0f, 0.0f), 0.0001f);
        assertEquals(16.0f, DarkmatterCut.Server.directDamage(5.0f, 0.0f), 0.0001f);
        assertEquals(4.56f, DarkmatterCut.Server.directDamage(0.0f, 3.0f), 0.0001f);
        assertEquals(3.6f, DarkmatterCut.Server.directDamage(0.0f, 5.0f), 0.0001f);
        assertEquals(0.56, DarkmatterCut.Server.knockback(3.0f), 0.0001);
        assertEquals(0.3f, DarkmatterCut.Server.penetration(3.0f), 0.0001f);
        assertEquals(2.7f, DarkmatterCut.Server.matterCost(3.0f, 1), 0.0001f);
    }
}
