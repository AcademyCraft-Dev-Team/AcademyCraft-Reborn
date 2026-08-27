package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlurPanelWidgetTest {

    private fun FrameLayoutWidget.measureAndLayout(width: Float, height: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, width),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, height)
        )
        layout(0f, 0f, width, height)
    }

    @Test
    fun `blur panel registers screen-space region on render`() {
        val root = FrameLayoutWidget()
        val panel = BlurPanelWidget(12f)
        val lp = FrameLayoutWidget.LayoutParams()
        lp.size(50f, 30f)
        lp.gravity = Gravity.CENTER
        panel.layoutParams = lp
        root.addChild("panel", panel)
        root.measureAndLayout(200f, 200f)

        val context = RenderContext()
        panel.render(context)

        assertEquals(1, context.blurRegions.size)
        val region = context.blurRegions[0]
        assertEquals(75f, region.x)
        assertEquals(85f, region.y)
        assertEquals(50f, region.width)
        assertEquals(30f, region.height)
        assertEquals(12f, region.radius)
    }

    @Test
    fun `hidden blur panel registers no region`() {
        val root = FrameLayoutWidget()
        val panel = BlurPanelWidget()
        panel.layoutParams = FrameLayoutWidget.LayoutParams().size(50f, 30f)
        panel.visibility = Widget.Visibility.INVISIBLE
        root.addChild("panel", panel)
        root.measureAndLayout(200f, 200f)

        val context = RenderContext()
        panel.render(context)
        assertTrue(context.blurRegions.isEmpty())
    }

    @Test
    fun `multiple panels with same radius register separately`() {
        val root = FrameLayoutWidget()
        val a = BlurPanelWidget(8f).apply { layoutParams = FrameLayoutWidget.LayoutParams().size(20f, 20f) }
        val b = BlurPanelWidget(8f).apply { layoutParams = FrameLayoutWidget.LayoutParams().size(20f, 20f) }
        root.addChild("a", a)
        root.addChild("b", b)
        root.measureAndLayout(100f, 100f)

        val context = RenderContext()
        a.render(context)
        b.render(context)
        assertEquals(2, context.blurRegions.size)
        assertTrue(context.blurRegions.all { it.radius == 8f })
    }

    @Test
    fun `blur panel with onClick consumes press and invokes callback`() {
        var clicks = 0
        val root = FrameLayoutWidget()
        val panel = BlurPanelWidget(8f).apply {
            layoutParams = FrameLayoutWidget.LayoutParams().size(50f, 30f)
            onClick = { clicks++ }
        }
        root.addChild("panel", panel)
        root.measureAndLayout(200f, 200f)

        val event = MouseEvent.createPressEvent(25.0, 15.0, 0)
        root.dispatchEvent(event)

        assertTrue(event.isConsumed, "press over clickable blur panel must be consumed")
        assertEquals(1, clicks)
    }

    @Test
    fun `blur panel without onClick does not consume press`() {
        val panel = BlurPanelWidget(8f).apply {
            layoutParams = FrameLayoutWidget.LayoutParams().size(50f, 30f)
        }

        val event = MouseEvent.createPressEvent(25.0, 15.0, 0)
        panel.dispatchEvent(event)

        assertTrue(!event.isConsumed, "blur panel without onClick must not consume clicks")
    }
}
