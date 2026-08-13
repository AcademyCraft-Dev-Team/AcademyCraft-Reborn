package org.academy.internal.client.render.vfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirStrikeGroundEffectTest {
    @Test
    void preservesBothPackedLightmapCoordinates() {
        assertEquals(44, DirStrikeGroundData.VERTEX_STRIDE);
        assertEquals(240, DirStrikeGroundData.packedBlockCoordinate(0x00F000F0));
        assertEquals(240, DirStrikeGroundData.packedSkyCoordinate(0x00F000F0));
        assertEquals(112, DirStrikeGroundData.packedBlockCoordinate(0x00B00070));
        assertEquals(176, DirStrikeGroundData.packedSkyCoordinate(0x00B00070));
    }

    @Test
    void packsRotatedNormalsForShaderPackRendering() {
        assertEquals(1.0f,
                DirStrikeGroundData.unpackNormal(DirStrikeGroundData.packNormal(1.0f)),
                1.0f / 127.0f);
        assertEquals(-1.0f,
                DirStrikeGroundData.unpackNormal(DirStrikeGroundData.packNormal(-1.0f)),
                1.0f / 127.0f);
        assertEquals(0.5f,
                DirStrikeGroundData.unpackNormal(DirStrikeGroundData.packNormal(0.5f)),
                1.0f / 127.0f);
    }

    @Test
    void centralizedTimelinePreservesRiseHoldAndFall() {
        assertEquals(0.0f, DirStrikeGroundEffect.motion(0.0f, 18, 20), 1.0e-6f);
        var rising = DirStrikeGroundEffect.motion(3.0f, 18, 20);
        assertTrue(rising > 0.0f && rising < 1.0f);
        assertEquals(1.0f, DirStrikeGroundEffect.motion(6.0f, 18, 20), 1.0e-6f);
        assertEquals(1.0f, DirStrikeGroundEffect.motion(26.0f, 18, 20), 1.0e-6f);
        var falling = DirStrikeGroundEffect.motion(32.0f, 18, 20);
        assertTrue(falling > 0.0f && falling < 1.0f);
        assertEquals(0.0f, DirStrikeGroundEffect.motion(38.0f, 18, 20), 1.0e-6f);
    }
}
