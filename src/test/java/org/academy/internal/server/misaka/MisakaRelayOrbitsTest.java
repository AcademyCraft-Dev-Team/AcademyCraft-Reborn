package org.academy.internal.server.misaka;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaRelayOrbitsTest {
    @Test
    void visualOrbitYClampsConfiguredHeightToWorldCeiling() {
        // Overworld-like: minY=-64, logicalHeight=384 → ceiling=304
        assertEquals(304.0, MisakaRelayOrbits.visualOrbitY(1000, -64, 384), 1.0e-6);
        assertEquals(200.0, MisakaRelayOrbits.visualOrbitY(200, -64, 384), 1.0e-6);
    }

    @Test
    void skyVisibilityWindowIsFirstQuarterOfOrbit() {
        int seed = 0;
        // angle = gameTime * 0.01; TWO_PI ≈ 6.2832 → period ≈ 628.32 ticks
        assertTrue(MisakaRelayOrbits.isSkyModelVisible(0, seed));
        assertTrue(MisakaRelayOrbits.isSkyModelVisible(100, seed));
        assertFalse(MisakaRelayOrbits.isSkyModelVisible(400, seed));
    }

    @Test
    void crashDurationScalesFromLaunchTicks() {
        assertEquals(1000, MisakaRelayOrbits.crashDurationTicks(1200));
        assertEquals(2000, MisakaRelayOrbits.crashDurationTicks(2400));
        assertEquals(20, MisakaRelayOrbits.crashDurationTicks(1));
        double vmax = MisakaRelayOrbits.crashMaxSpeed(240.0, 1000);
        assertEquals(0.24, vmax, 1.0e-6);
        assertTrue(MisakaRelayOrbits.crashAccel(vmax) < 0.0);
    }
}
