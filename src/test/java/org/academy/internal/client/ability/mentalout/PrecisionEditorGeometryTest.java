package org.academy.internal.client.ability.mentalout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrecisionEditorGeometryTest {
    @Test
    void narrowLayoutRetainsFiveNodeColumns() {
        var layout = PrecisionEditorGeometry.layout(480, 270);
        var columns = (layout.canvasW() + 8) / (PrecisionOperationScreen.NODE_W + 8);
        var rows = (layout.canvasH() + 8) / (PrecisionOperationScreen.MIN_NODE_H + 8);

        assertTrue(layout.compactLeft());
        assertTrue(layout.compactRight());
        assertTrue(columns >= 5);
        assertTrue(rows >= 4);
    }

    @Test
    void normalLayoutRetainsSixNodeColumns() {
        var layout = PrecisionEditorGeometry.layout(854, 480);
        var columns = (layout.canvasW() + 8) / (PrecisionOperationScreen.NODE_W + 8);

        assertTrue(columns >= 6);
    }

    @Test
    void cursorCenteredZoomKeepsGraphPointStable() {
        var graphX = PrecisionEditorGeometry.screenToGraph(230.0, 20.0, 15.0, 1.0);
        var graphY = PrecisionEditorGeometry.screenToGraph(140.0, 30.0, -5.0, 1.0);
        var view = PrecisionEditorGeometry.zoomAt(230.0, 140.0, 20.0, 30.0,
                15.0, -5.0, 1.0, 1.5);

        assertEquals(graphX, PrecisionEditorGeometry.screenToGraph(
                230.0, 20.0, view.panX(), view.zoom()), 0.00001);
        assertEquals(graphY, PrecisionEditorGeometry.screenToGraph(
                140.0, 30.0, view.panY(), view.zoom()), 0.00001);
    }

    @Test
    void zoomIsClampedToEditorLimits() {
        assertEquals(PrecisionOperationScreen.MIN_ZOOM,
                PrecisionEditorGeometry.zoomAt(0, 0, 0, 0, 0, 0, 1.0, 0.1).zoom());
        assertEquals(PrecisionOperationScreen.MAX_ZOOM,
                PrecisionEditorGeometry.zoomAt(0, 0, 0, 0, 0, 0, 1.0, 4.0).zoom());
    }

    @Test
    void reverseDragProducesNormalizedSelectionBounds() {
        var bounds = PrecisionEditorGeometry.selectionBounds(120, 90, 20, 30);

        assertEquals(20, bounds.left());
        assertEquals(30, bounds.top());
        assertEquals(120, bounds.right());
        assertEquals(90, bounds.bottom());
        assertTrue(bounds.exceeds(3));
    }

    @Test
    void selectionBoundsIncludeIntersectingNodesOnly() {
        var bounds = PrecisionEditorGeometry.selectionBounds(10, 10, 80, 60);

        assertTrue(bounds.intersects(70, 50, 20, 20));
        assertTrue(bounds.intersects(10, 10, 1, 1));
        assertFalse(bounds.intersects(81, 61, 20, 20));
    }
}
