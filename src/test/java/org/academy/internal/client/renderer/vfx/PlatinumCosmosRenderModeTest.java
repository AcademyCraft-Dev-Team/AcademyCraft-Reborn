package org.academy.internal.client.renderer.vfx;

import org.academy.internal.client.render.vfx.PlatinumCosmosRenderMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatinumCosmosRenderModeTest {
    @Test
    void selectsNormalExactAndFallbackWithoutDuplicateModes() {
        assertEquals(
                PlatinumCosmosRenderMode.NORMAL,
                PlatinumCosmosRenderMode.select(false, false)
        );
        assertEquals(
                PlatinumCosmosRenderMode.NORMAL,
                PlatinumCosmosRenderMode.select(false, true)
        );
        assertEquals(
                PlatinumCosmosRenderMode.EXACT,
                PlatinumCosmosRenderMode.select(true, true)
        );
        assertEquals(
                PlatinumCosmosRenderMode.FALLBACK,
                PlatinumCosmosRenderMode.select(true, false)
        );
    }
}
