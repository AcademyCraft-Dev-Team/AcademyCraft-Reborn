package org.academy.internal.client.render.vfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirStrikeGroundEffectTest {
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
