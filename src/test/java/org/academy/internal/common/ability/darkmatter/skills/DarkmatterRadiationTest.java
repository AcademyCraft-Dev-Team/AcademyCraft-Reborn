package org.academy.internal.common.ability.darkmatter.skills;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterRadiationTest {
    @Test
    void damageCombinesFlatAndDarkmatterComponents() {
        assertEquals(4.0f, DarkmatterRadiation.Server.damage(20, 1), 0.0001f);
        assertEquals(12.0f, DarkmatterRadiation.Server.damage(10_000, 1), 0.0001f);
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
}
