package org.academy.api.common.vfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlasmaChargeVisualsTest {
    @Test void convergesAtThreeSecondsThenGrowsUntilTwelve() {
        assertEquals(0f, PlasmaChargeVisuals.convergence(0));
        assertTrue(PlasmaChargeVisuals.convergence(20f / 240f) > 0);
        assertEquals(1f, PlasmaChargeVisuals.convergence(60f / 240f));
        assertEquals(0.62f, PlasmaChargeVisuals.formation(60f / 240f));
        assertEquals(1f, PlasmaChargeVisuals.formation(1));
        float previous = 0;
        for (int tick = 0; tick <= 240; tick++) {
            float formation = PlasmaChargeVisuals.formation(tick / 240f);
            assertTrue(formation >= previous && formation <= 1f);
            previous = formation;
        }
    }
}
