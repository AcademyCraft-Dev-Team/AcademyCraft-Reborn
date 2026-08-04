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
}
