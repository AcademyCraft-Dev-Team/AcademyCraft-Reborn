package org.academy.desktop.uieditor.preview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanvasTransformTest {

    @Test
    fun docViewRoundTrip() {
        val t = CanvasTransform(1.5f, 120f, 80f)
        val vx = t.docToViewX(200f)
        val vy = t.docToViewY(100f)
        assertEquals(200f, t.viewToDocX(vx), 0.001f)
        assertEquals(100f, t.viewToDocY(vy), 0.001f)
    }

    @Test
    fun zoomAtKeepsAnchorFixed() {
        val t = CanvasTransform(1f, 0f, 0f).zoomAt(100f, 50f, 2f)
        assertEquals(2f, t.scale)
        assertEquals(100f, t.docToViewX(100f), 0.001f)
        assertEquals(50f, t.docToViewY(50f), 0.001f)
    }

    @Test
    fun zoomIsClamped() {
        val big = CanvasTransform(1f, 0f, 0f).zoomAt(0f, 0f, 10_000f)
        val small = CanvasTransform(1f, 0f, 0f).zoomAt(0f, 0f, 0.000001f)
        assertEquals(CanvasTransform.MAX_SCALE, big.scale)
        assertEquals(CanvasTransform.MIN_SCALE, small.scale)
    }

    @Test
    fun fitCentersDocumentWithPadding() {
        val t = CanvasTransform.IDENTITY.fitted(200f, 100f, 1000f, 500f, 24f)
        assertTrue(t.scale > 1f && t.scale <= CanvasTransform.MAX_SCALE)
        assertEquals(500f, t.docToViewX(100f), 0.01f)
        assertEquals(250f, t.docToViewY(50f), 0.01f)
    }

    @Test
    fun centeringOnPutsPointAtViewportCenter() {
        val t = CanvasTransform(2f, -30f, 40f).centeredOn(50f, 25f, 1000f, 500f)
        assertEquals(500f, t.docToViewX(50f), 0.001f)
        assertEquals(250f, t.docToViewY(25f), 0.001f)
    }

    @Test
    fun niceStepUsesLadder() {
        assertEquals(2f, niceStep(1.5f))
        assertEquals(5f, niceStep(4f))
        assertEquals(10f, niceStep(9f))
        assertEquals(20f, niceStep(11f))
    }
}
