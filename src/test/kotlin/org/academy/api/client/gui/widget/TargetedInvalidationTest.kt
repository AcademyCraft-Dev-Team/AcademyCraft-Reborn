package org.academy.api.client.gui.widget

import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TargetedInvalidationTest {
    private class OwnContentContainer : AbstractWidgetContainer() {
        var renderInternalCount = 0
            private set

        override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams = FrameLayoutWidget.LayoutParams()
        override fun generateLayoutParams(p: WidgetContainer.LayoutParams): WidgetContainer.LayoutParams =
            FrameLayoutWidget.LayoutParams(p)

        override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean = p is FrameLayoutWidget.LayoutParams

        override fun renderInternal(context: RenderContext) {
            renderInternalCount++
            context.submit(FillRectDrawCommand(width, height, 1f, 1f, 1f, 1f))
        }
    }

    private fun FrameLayoutWidget.measureAndLayout(w: Float, h: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, w),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, h)
        )
        layout(0f, 0f, w, h)
    }

    @Test
    fun `container invalidate does not dirty descendants`() {
        val root = FrameLayoutWidget()
        val parent = OwnContentContainer()
        val child = OwnContentContainer()
        parent.addChild("child", child)
        root.addChild("parent", parent)
        root.measureAndLayout(100f, 100f)

        root.render(RenderContext())
        assertFalse(child.isRenderDirty)

        parent.invalidate()
        assertTrue(parent.isRenderDirty)
        assertFalse(child.isRenderDirty, "定向失效不应递归脏后代")
    }

    @Test
    fun `child content change does not re-record sibling cache`() {
        val root = FrameLayoutWidget()
        val a = OwnContentContainer()
        val b = OwnContentContainer()
        root.addChild("a", a)
        root.addChild("b", b)
        root.measureAndLayout(100f, 100f)

        root.render(RenderContext())
        assertEquals(1, a.renderInternalCount)
        assertEquals(1, b.renderInternalCount)

        a.invalidate()
        root.render(RenderContext())
        assertEquals(2, a.renderInternalCount, "自身失效应重录自身")
        assertEquals(1, b.renderInternalCount, "兄弟控件内容变化不应重录兄弟缓存")
    }

    @Test
    fun `layout size change re-records self but position change does not`() {
        val root = FrameLayoutWidget()
        val box = OwnContentContainer()
        root.addChild("box", box)
        root.measureAndLayout(100f, 100f)

        root.render(RenderContext())
        assertEquals(1, box.renderInternalCount)

        box.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 100f),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 100f)
        )
        box.layout(0f, 0f, 100f, 100f) // 尺寸未变, frame 未变 -> 无失效
        root.render(RenderContext())
        assertEquals(1, box.renderInternalCount)

        box.layout(10f, 20f, 110f, 120f) // 位置变化 -> 位姿重组, 不重录
        root.render(RenderContext())
        assertEquals(1, box.renderInternalCount, "位置变化应走位姿重组而非重录")

        box.layout(0f, 0f, 200f, 200f) // 尺寸变化 -> 重录自身
        root.render(RenderContext())
        assertEquals(2, box.renderInternalCount, "尺寸变化应重录自身内容 (几何烘焙尺寸)")
    }
}
