package org.academy.internal.common.ability.meltdowner.skills.lv4;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JetStrikeTest {
    @Test
    void directionIsNormalizedAndZeroAimIsRejected() {
        assertEquals(new Vec3(1, 0, 0), JetStrike.normalizeDirection(new Vec3(2, 0, 0)));
        assertNull(JetStrike.normalizeDirection(Vec3.ZERO));
    }

    @Test
    void damageAndGeometryMatchReferenceContract() {
        assertEquals(10.0f, JetStrike.calculateDamage(1.0f, 1.0f));
        assertEquals(22.5f, JetStrike.calculateDamage(1.5f, 1.5f));
        assertEquals(0.0f, JetStrike.calculateDamage(-1.0f, 1.0f));
        assertEquals(8.0, JetStrike.DISTANCE);
        assertEquals(3.25, JetStrike.DAMAGE_RADIUS);
    }

    @Test
    void dashVelocityUsesAtomicJetImpulseSpeed() {
        var velocity = JetStrike.calculateDashVelocity(new Vec3(8.0, 0.0, 0.0));

        assertEquals(JetStrike.DASH_SPEED, velocity.length(), 0.0001);
        assertEquals(JetStrike.DASH_SPEED, velocity.x, 0.0001);
        assertEquals(0.0, velocity.y, 0.0001);
        assertEquals(0.0, velocity.z, 0.0001);
        assertEquals(Vec3.ZERO, JetStrike.calculateDashVelocity(Vec3.ZERO));
    }
}
