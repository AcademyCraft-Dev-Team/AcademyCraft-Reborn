package org.academy.api.client.gui.widget

import org.academy.api.client.gui.dsl.matchParent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PagerLayoutWidgetTest {

    private fun PagerLayoutWidget.measureAndLayout(width: Float, height: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, width),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, height)
        )
        layout(0f, 0f, width, height)
    }

    private fun PagerLayoutWidget.withTwoPages(): PagerLayoutWidget {
        addChild("a", FrameLayoutWidget().apply { matchParent() })
        addChild("b", FrameLayoutWidget().apply { matchParent() })
        return this
    }

    @Test
    fun `page 0 shows first page in place and second offscreen right`() {
        val pager = PagerLayoutWidget().withTwoPages()
        pager.measureAndLayout(200f, 100f)
        pager.jumpToPage(0)

        pager.render(RenderContext())

        assertEquals(0f, pager.children["a"]!!.translationX)
        assertEquals(200f, pager.children["b"]!!.translationX)
    }

    @Test
    fun `page 1 slides first page offscreen left and second into place`() {
        val pager = PagerLayoutWidget().withTwoPages()
        pager.measureAndLayout(200f, 100f)
        pager.switchToPage(1, animate = false)

        pager.render(RenderContext())

        assertEquals(-200f, pager.children["a"]!!.translationX)
        assertEquals(0f, pager.children["b"]!!.translationX)
    }

    @Test
    fun `switchToPage ignores out of range indices`() {
        val pager = PagerLayoutWidget().withTwoPages()
        pager.measureAndLayout(200f, 100f)

        pager.switchToPage(5, animate = false)
        assertEquals(0, pager.currentPage)

        pager.switchToPage(-1, animate = false)
        assertEquals(0, pager.currentPage)
    }

    @Test
    fun `switching back and forth returns to the original layout`() {
        val pager = PagerLayoutWidget().withTwoPages()
        pager.measureAndLayout(200f, 100f)
        pager.switchToPage(1, animate = false)
        pager.switchToPage(0, animate = false)

        pager.render(RenderContext())

        assertEquals(0f, pager.children["a"]!!.translationX)
        assertEquals(200f, pager.children["b"]!!.translationX)
    }

    // ============ 点击裁剪 (离屏页不可点击) ============

    private fun PagerLayoutWidget.withButtonOn(page: String): ButtonWidget {
        val button = ButtonWidget().apply {
            onClickListener = OnClickListener { clicks++ }
            layoutParams = FrameLayoutWidget.LayoutParams().size(50f, 50f)
        }
        (children[page] as FrameLayoutWidget).addChild("btn", button)
        return button
    }

    private var clicks = 0

    @Test
    fun `clicks outside the pager bounds do not reach offscreen pages`() {
        val pager = PagerLayoutWidget().withTwoPages()
        pager.withButtonOn("b")
        pager.measureAndLayout(200f, 100f)
        pager.jumpToPage(0) // page b 离屏在右侧 x=200..400
        pager.render(RenderContext())

        // 点击在 Pager 可见矩形 (0..200) 之外, 但落在离屏页 b 的按钮上.
        val event = MouseEvent.createPressEvent(220.0, 10.0, 0)
        pager.dispatchEvent(event)

        assertEquals(0, clicks, "offscreen page content must not be clickable")
        assertFalse(event.isConsumed)
    }

    @Test
    fun `clicks inside the pager bounds still reach the visible page`() {
        val pager = PagerLayoutWidget().withTwoPages()
        pager.withButtonOn("a")
        pager.measureAndLayout(200f, 100f)
        pager.jumpToPage(0)
        pager.render(RenderContext())

        val event = MouseEvent.createPressEvent(10.0, 10.0, 0)
        pager.dispatchEvent(event)

        assertEquals(1, clicks)
        assertTrue(event.isConsumed)
    }
}
