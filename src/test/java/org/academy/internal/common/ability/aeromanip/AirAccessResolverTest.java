package org.academy.internal.common.ability.aeromanip;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirAccessResolverTest {
    @Test
    void exposedSampleAllowsRecovery() {
        assertTrue(AirAccessResolver.canRecoverFromSamples(List.of(
                new AirAccessResolver.AirSample(false, false),
                new AirAccessResolver.AirSample(false, true)
        )));
    }

    @Test
    void completeSubmersionPreventsRecovery() {
        assertFalse(AirAccessResolver.canRecoverFromSamples(List.of(
                new AirAccessResolver.AirSample(true, false),
                new AirAccessResolver.AirSample(true, false)
        )));
    }

    @Test
    void completeBurialPreventsRecovery() {
        assertFalse(AirAccessResolver.canRecoverFromSamples(List.of(
                new AirAccessResolver.AirSample(false, false),
                new AirAccessResolver.AirSample(false, false)
        )));
    }

    @Test
    void samplesBodyCornersCenterAndEyeHeight() {
        var points = AirAccessResolver.samplePoints(new AABB(0, 10, 0, 1, 12, 1), 11.6);
        assertEquals(27, points.size());
        assertTrue(points.stream().anyMatch(point -> point.y == 11.6));
    }
}
