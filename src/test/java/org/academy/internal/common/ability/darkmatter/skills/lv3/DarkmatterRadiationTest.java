package org.academy.internal.common.ability.darkmatter.skills.lv3;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterRadiationTest {
    @Test
    void phaseDamageUsesTheSpecifiedContinuousCurves() {
        assertEquals(0.9f, DarkmatterRadiation.Server.alphaPulseDamage(1), 0.0001f);
        assertEquals(1.7f, DarkmatterRadiation.Server.alphaPulseDamage(3), 0.0001f);
        assertEquals(2.5f, DarkmatterRadiation.Server.alphaPulseDamage(5), 0.0001f);
        assertEquals(0.5f, DarkmatterRadiation.Server.betaPulseDamage(1), 0.0001f);
        assertEquals(1.0f, DarkmatterRadiation.Server.betaPulseDamage(3), 0.0001f);
        assertEquals(1.5f, DarkmatterRadiation.Server.betaPulseDamage(5), 0.0001f);
        assertEquals(4.0f, DarkmatterRadiation.Server.exposureBurstDamage(3), 0.0001f);
    }

    @Test
    void hemisphereRejectsTargetsBehindCaster() {
        assertTrue(DarkmatterRadiation.Server.insideFrontHemisphere(
                Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 0, 20)));
        assertFalse(DarkmatterRadiation.Server.insideFrontHemisphere(
                Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 0, -1)));
        assertFalse(DarkmatterRadiation.Server.insideFrontHemisphere(
                Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 0, 33)));
    }

    @Test
    void secondMilestoneExpandsBothPhaseRangesAndPulseCadence() {
        assertEquals(14.0, DarkmatterRadiation.Server.alphaRange(1.0f, 1), 0.0001);
        assertEquals(16.1, DarkmatterRadiation.Server.alphaRange(1.0f, 2), 0.0001);
        assertEquals(12.0, DarkmatterRadiation.Server.betaRange(1.0f, 1), 0.0001);
        assertEquals(13.8, DarkmatterRadiation.Server.betaRange(1.0f, 2), 0.0001);
        assertEquals(5, DarkmatterRadiation.Server.pulseInterval(1));
        assertEquals(4, DarkmatterRadiation.Server.pulseInterval(2));
    }

    @Test
    void phaseAnglesMoveInOppositeDirections() {
        assertEquals(29.0, DarkmatterRadiation.Server.alphaHalfAngle(1), 0.0001);
        assertEquals(48.0, DarkmatterRadiation.Server.betaHalfAngle(1), 0.0001);
        assertEquals(17.0, DarkmatterRadiation.Server.alphaHalfAngle(5), 0.0001);
        assertEquals(80.0, DarkmatterRadiation.Server.betaHalfAngle(5), 0.0001);
    }

    @Test
    void thirdMilestoneLowersExposureThresholdAndAddsGammaBlades() {
        assertEquals(20, DarkmatterRadiation.Server.exposurePulseTicks(2));
        assertEquals(15, DarkmatterRadiation.Server.exposurePulseTicks(3));
        assertEquals(3, DarkmatterRadiation.Server.gammaFeatherCount(1, 2));
        assertEquals(5, DarkmatterRadiation.Server.gammaFeatherCount(1, 3));
        assertEquals(2, DarkmatterRadiation.Server.alphaFeatherCount(3));
        assertEquals(3, DarkmatterRadiation.Server.alphaFeatherCount(5));
        assertEquals(1.8f, DarkmatterRadiation.Server.alphaFeatherDamage(3), 0.0001f);
        assertEquals(2.0f, DarkmatterRadiation.Server.maintenanceCost(0), 0.0001f);
        assertEquals(1.8f, DarkmatterRadiation.Server.maintenanceCost(1), 0.0001f);
    }
}
