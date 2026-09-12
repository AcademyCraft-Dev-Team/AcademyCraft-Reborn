package org.academy.internal.gui.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityRadarThumbnailRendererTest {
    @Test
    void scaleFramesAPlayerHeadInsideTheIcon() {
        assertEquals(13, EntityRadarThumbnailRenderer.fitScale(0.6, 1.8, 10));
    }

    @Test
    void scaleCropsBroadEntitiesAroundTheirHead() {
        assertEquals(8, EntityRadarThumbnailRenderer.fitScale(4.0, 2.0, 10));
    }

    @Test
    void degenerateBoundsStayFiniteAndBounded() {
        assertEquals(16, EntityRadarThumbnailRenderer.fitScale(0.0, 0.0, 10));
    }

    @Test
    void focusUsesEyeHeight() {
        assertEquals(1.62f, EntityRadarThumbnailRenderer.focusHeight(1.8, 1.62));
    }

    @Test
    void focusFallsBackToBodyCenter() {
        assertEquals(0.9f, EntityRadarThumbnailRenderer.focusHeight(1.8, Double.NaN));
    }

    @Test
    void admittedThumbnailRemainsVisibleAfterThePerFrameBudgetIsFull() {
        assertTrue(EntityRadarThumbnailRenderer.canRenderThumbnail(true, false, 64));
    }

    @Test
    void admissionBudgetDefersOnlyNewNonPriorityThumbnails() {
        assertFalse(EntityRadarThumbnailRenderer.canRenderThumbnail(false, false, 64));
        assertTrue(EntityRadarThumbnailRenderer.canRenderThumbnail(false, false, 63));
        assertTrue(EntityRadarThumbnailRenderer.canRenderThumbnail(false, true, 64));
    }

    @Test
    void adjustedFocusMovesTheThumbnailUpByOneLogicalPixel() {
        assertEquals(1.62f - 1.0f / 13.0f,
                EntityRadarThumbnailRenderer.adjustedFocusHeight(1.8, 1.62, 13));
    }
}
