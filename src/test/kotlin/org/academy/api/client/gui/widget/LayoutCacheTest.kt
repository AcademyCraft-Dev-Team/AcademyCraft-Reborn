package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LayoutCacheTest {
    private class CountLeaf : AbstractWidget() {
        var measureCount = 0
            private set

        override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
            measureCount++
            setMeasuredDimension(10f, 10f)
        }
    }

    private class CountContainer : AbstractWidgetContainer() {
        var measureCount = 0
            private set
        var onLayoutCount = 0
            private set

        override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams = FrameLayoutWidget.LayoutParams()
        override fun generateLayoutParams(p: WidgetContainer.LayoutParams): WidgetContainer.LayoutParams =
            FrameLayoutWidget.LayoutParams(p)

        override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean = p is FrameLayoutWidget.LayoutParams

        override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
            measureCount++
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        override fun onLayout() {
            onLayoutCount++
            super.onLayout()
        }
    }

    private val spec100 = MeasureSpec(MeasureSpec.Mode.EXACTLY, 100f)
    private val spec50 = MeasureSpec(MeasureSpec.Mode.EXACTLY, 50f)

    @Test
    fun `unchanged measure spec skips onMeasure`() {
        val leaf = CountLeaf()
        leaf.measure(spec100, spec50)
        assertEquals(1, leaf.measureCount)

        leaf.measure(spec100, spec50)
        assertEquals(1, leaf.measureCount, "spec 未变不应重新测量")

        leaf.measure(spec100, spec50)
        assertEquals(1, leaf.measureCount)
    }

    @Test
    fun `requestLayout forces re-measure even with unchanged spec`() {
        val leaf = CountLeaf()
        leaf.measure(spec100, spec50)
        assertEquals(1, leaf.measureCount)

        leaf.requestLayout()
        leaf.measure(spec100, spec50)
        assertEquals(2, leaf.measureCount, "requestLayout 应强制重测 (PFLAG_FORCE_LAYOUT)")
    }

    @Test
    fun `force layout propagates to ancestors`() {
        val parent = CountContainer()
        val child = CountLeaf()
        parent.addChild("child", child)

        parent.measure(spec100, spec50)
        assertEquals(1, parent.measureCount)
        assertEquals(1, child.measureCount)

        parent.measure(spec100, spec50)
        assertEquals(1, parent.measureCount, "未变化不应重测")
        assertEquals(1, child.measureCount)

        child.requestLayout()
        parent.measure(spec100, spec50)
        assertEquals(2, parent.measureCount, "子 requestLayout 应强制祖先重测")
        assertEquals(2, child.measureCount)
    }

    @Test
    fun `layout early-out skips onLayout on unchanged frame`() {
        val c = CountContainer()
        c.measure(spec100, spec50)
        c.layout(0f, 0f, 100f, 50f)
        assertEquals(1, c.onLayoutCount)

        c.layout(0f, 0f, 100f, 50f)
        assertEquals(1, c.onLayoutCount, "frame 未变且未重测不应再 onLayout (setFrame 早退)")
    }

    @Test
    fun `re-measured container re-runs onLayout despite unchanged frame`() {
        val c = CountContainer()
        c.measure(spec100, spec50)
        c.layout(0f, 0f, 100f, 50f)
        assertEquals(1, c.onLayoutCount)

        c.requestLayout()
        c.measure(spec100, spec50)
        c.layout(0f, 0f, 100f, 50f)
        assertEquals(2, c.onLayoutCount, "重测后应重排子树 (PFLAG_LAYOUT_REQUIRED)")
    }

    @Test
    fun `matchesSpecSize skips onMeasure when exact size equals measured`() {
        val leaf = CountLeaf()
        leaf.measure(spec100, spec50)
        assertEquals(1, leaf.measureCount)

        leaf.measure(MeasureSpec(MeasureSpec.Mode.EXACTLY, 10f), MeasureSpec(MeasureSpec.Mode.EXACTLY, 10f))
        assertEquals(1, leaf.measureCount, "EXACTLY 尺寸与现 measured 相同应跳过 onMeasure")
    }

    @Test
    fun `unspecified measure cache reused after spec changed`() {
        val leaf = CountLeaf()
        val unspecified = MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
        leaf.measure(unspecified, unspecified)
        assertEquals(1, leaf.measureCount)

        leaf.measure(spec100, spec50)
        assertEquals(2, leaf.measureCount)

        leaf.measure(unspecified, unspecified)
        assertEquals(2, leaf.measureCount, "UNSPECIFIED 测量缓存应命中 (回收场景)")
    }

    @Test
    fun `onLayoutComplete fires only when frame actually changed`() {
        val c = CountContainer()
        var fireCount = 0
        c.onLayoutComplete = { fireCount++ }
        c.measure(spec100, spec50)
        c.layout(0f, 0f, 100f, 50f)
        assertEquals(1, fireCount, "首次 layout frame 变化应触发")

        c.requestLayout()
        c.measure(spec100, spec50)
        c.layout(0f, 0f, 100f, 50f)
        assertEquals(1, fireCount, "重测但 frame 未变不应触发 onLayoutComplete (P2-9)")
    }
}
