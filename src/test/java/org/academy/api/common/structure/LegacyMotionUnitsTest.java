package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyMotionUnitsTest {
    @Test
    void velocityConversionPreservesTheDistanceTraveledInOneSecond() {
        var perTick = new Vec3(1.5, -0.25, 0.0);
        var perSecond = LegacyMotionUnits.velocityPerSecond(perTick);

        assertEquals(new Vec3(30.0, -5.0, 0.0), perSecond);
        assertEquals(perTick, LegacyMotionUnits.velocityPerTick(perSecond));
        assertEquals(30.0, LegacyMotionUnits.velocityPerSecond(1.5));
        assertEquals(1.5, LegacyMotionUnits.velocityPerTick(30.0));
    }

    @Test
    void accelerationConversionAccountsForBothTimeDimensions() {
        var legacyGravity = new Vec3(0.0, -0.04, 0.0);

        assertEquals(new Vec3(0.0, -16.0, 0.0),
                LegacyMotionUnits.accelerationPerSecondSquared(legacyGravity));
        assertEquals(legacyGravity, LegacyMotionUnits.accelerationPerTickSquared(
                new Vec3(0.0, -16.0, 0.0)));
        assertEquals(16.0, LegacyMotionUnits.accelerationPerSecondSquared(0.04));
        assertEquals(0.04, LegacyMotionUnits.accelerationPerTickSquared(16.0));
    }

    @Test
    void nonFiniteInputsAndOverflowAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LegacyMotionUnits.velocityPerSecond(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> LegacyMotionUnits.velocityPerTick(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> LegacyMotionUnits.accelerationPerSecondSquared(Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> LegacyMotionUnits.velocityPerSecond(new Vec3(Double.MAX_VALUE, 0.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> LegacyMotionUnits.accelerationPerTickSquared(
                        new Vec3(0.0, Double.NEGATIVE_INFINITY, 0.0)));
    }
}
