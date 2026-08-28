package org.academy.internal.common.ability.aeromanip.skills.lv4;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightTest {
    @Test
    void everyMilestoneRaisesCreativeFlightSpeed() {
        assertEquals(0.035f, Flight.flyingSpeed(0));
        assertEquals(0.05f, Flight.flyingSpeed(1));
        assertEquals(0.065f, Flight.flyingSpeed(2));
        assertEquals(0.08f, Flight.flyingSpeed(3));
        assertEquals(0.08f, Flight.flyingSpeed(99));
    }

    @Test
    void compressedAirIsOnlyConsumedWhileActuallyFlyingAndMoving() {
        assertFalse(Flight.shouldConsumeCompressedAir(true, Vec3.ZERO));
        assertFalse(Flight.shouldConsumeCompressedAir(false, new Vec3(0.5, 0.0, 0.0)));
        assertTrue(Flight.shouldConsumeCompressedAir(true, new Vec3(0.02, 0.0, 0.0)));
    }
}
