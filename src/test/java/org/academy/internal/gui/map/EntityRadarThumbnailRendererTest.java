package org.academy.internal.gui.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityRadarThumbnailRendererTest {
    @Test
    void scaleFramesAPlayerHeadInsideTheIcon() {
        assertEquals(16, EntityRadarThumbnailRenderer.fitScale(0.6, 1.8, 10));
    }

    @Test
    void scaleCropsBroadEntitiesAroundTheirHead() {
        assertEquals(11, EntityRadarThumbnailRenderer.fitScale(4.0, 2.0, 10));
    }

    @Test
    void degenerateBoundsStayFiniteAndBounded() {
        assertEquals(20, EntityRadarThumbnailRenderer.fitScale(0.0, 0.0, 10));
    }

    @Test
    void focusUsesEyeHeight() {
        assertEquals(1.62f, EntityRadarThumbnailRenderer.focusHeight(1.8, 1.62));
    }

    @Test
    void focusFallsBackToBodyCenter() {
        assertEquals(0.9f, EntityRadarThumbnailRenderer.focusHeight(1.8, Double.NaN));
    }
}
