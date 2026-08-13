package org.academy.internal.common.ability.accelerator.skills;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WingFlightDirectionTest {
    private static final double EPSILON = 1.0e-6;

    @Test
    void usesControlPacketViewInsteadOfStaleServerView() {
        var resolved = WingFlightDirection.resolve(new Vec3(0, 0, 1), 90.0f, 0.0f);

        assertEquals(-1.0, resolved.x, EPSILON);
        assertEquals(0.0, resolved.y, EPSILON);
        assertEquals(0.0, resolved.z, EPSILON);
    }

    @Test
    void clampsPitchToVanillaViewLimits() {
        var resolved = WingFlightDirection.resolve(new Vec3(0, 0, 1), 0.0f, 120.0f);

        assertEquals(0.0, resolved.x, EPSILON);
        assertEquals(-1.0, resolved.y, EPSILON);
        assertEquals(0.0, resolved.z, EPSILON);
    }

    @Test
    void rejectsNonFiniteClientAngles() {
        var fallback = new Vec3(0.25, 0.5, 0.75);

        assertEquals(fallback, WingFlightDirection.resolve(fallback, Float.NaN, 0.0f));
        assertEquals(fallback, WingFlightDirection.resolve(fallback, 0.0f, Float.POSITIVE_INFINITY));
    }
}
