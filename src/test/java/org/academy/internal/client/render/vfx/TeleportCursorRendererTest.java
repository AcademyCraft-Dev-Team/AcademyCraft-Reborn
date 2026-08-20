package org.academy.internal.client.render.vfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportCursorRendererTest {
    @Test
    void selectedPositiveZEdgeFacesPlayer() {
        assertEquals(0.0f, TeleportCursorRenderer.playerFacingRotation(0.0, 1.0), 1.0e-6f);
        assertEquals((float) (Math.PI / 2.0),
                TeleportCursorRenderer.playerFacingRotation(1.0, 0.0), 1.0e-6f);
        assertEquals((float) Math.PI,
                TeleportCursorRenderer.playerFacingRotation(0.0, -1.0), 1.0e-6f);
        assertEquals((float) (-Math.PI / 2.0),
                TeleportCursorRenderer.playerFacingRotation(-1.0, 0.0), 1.0e-6f);
    }

    @Test
    void coincidentPlayerUsesStableAuthoredHeading() {
        assertEquals(0.0f, TeleportCursorRenderer.playerFacingRotation(0.0, 0.0), 0.0f);
    }
}
