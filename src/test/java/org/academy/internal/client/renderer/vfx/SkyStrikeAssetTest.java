package org.academy.internal.client.renderer.vfx;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyStrikeAssetTest {
    @Test
    void generatedRuntimeTexturesRetainExpectedDimensionsAndAlpha() throws Exception {
        assertTexture("lightning_column.png", 512, 1024);
        assertTexture("lightning_ribbon.png", 1024, 256);
        assertTexture("impact_shockwave_ring.png", 512, 512);
        assertTexture("impact_flash.png", 512, 512);
    }

    private static void assertTexture(String name, int width, int height) throws Exception {
        var path = "/assets/academy/textures/ability/electromaster/skill/sky_strike/effect/" + name;
        try (var stream = SkyStrikeAssetTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            var image = ImageIO.read(stream);
            assertNotNull(image, path);
            assertEquals(width, image.getWidth());
            assertEquals(height, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha(), path);
        }
    }
}
