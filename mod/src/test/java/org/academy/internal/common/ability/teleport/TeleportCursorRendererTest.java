package org.academy.internal.common.ability.teleport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportCursorRendererTest {
    @Test
    void selectedPositiveZEdgeFacesPlayer() {
        assertEquals(0.0f, Teleport.Client.TeleportCursorRenderer.playerFacingRotation(0.0, 1.0), 1.0e-6f);
        assertEquals((float) (Math.PI / 2.0),
                Teleport.Client.TeleportCursorRenderer.playerFacingRotation(1.0, 0.0), 1.0e-6f);
        assertEquals((float) Math.PI,
                Teleport.Client.TeleportCursorRenderer.playerFacingRotation(0.0, -1.0), 1.0e-6f);
        assertEquals((float) (-Math.PI / 2.0),
                Teleport.Client.TeleportCursorRenderer.playerFacingRotation(-1.0, 0.0), 1.0e-6f);
    }

    @Test
    void coincidentPlayerUsesStableAuthoredHeading() {
        assertEquals(0.0f, Teleport.Client.TeleportCursorRenderer.playerFacingRotation(0.0, 0.0), 0.0f);
    }
}
