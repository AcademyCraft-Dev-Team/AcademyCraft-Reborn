package org.academy.api.client.gui.render

import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.widget.AbstractWidget
import org.academy.api.client.gui.widget.AbstractWidgetContainer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.WidgetContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubtreeCacheReplayTest {
    private class Fill : AbstractWidget() {
        override fun render(context: RenderContext) {
            context.submit(FillRectDrawCommand(10f, 10f, 1f, 1f, 1f, 1f))
        }
    }

    private class OwnContentContainer : AbstractWidgetContainer() {
        var renderInternalCount = 0
            private set

        override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams = FrameLayoutWidget.LayoutParams()
        override fun generateLayoutParams(p: WidgetContainer.LayoutParams): WidgetContainer.LayoutParams =
            FrameLayoutWidget.LayoutParams(p)

        override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean = p is FrameLayoutWidget.LayoutParams

        override fun renderInternal(context: RenderContext) {
            renderInternalCount++
            context.submit(FillRectDrawCommand(width, height, 1f, 1f, 1f, context.accumulatedAlpha))
        }
    }

    private fun FrameLayoutWidget.measureAndLayout(w: Float, h: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, w),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, h)
        )
        layout(0f, 0f, w, h)
    }

    private fun pose(command: SubmittedCommand) = command.pose.pose()

    @Test
    fun `static own content replay keeps identical poses`() {
        val root = FrameLayoutWidget()
        val box = OwnContentContainer()
        root.addChild("box", box)
        root.measureAndLayout(100f, 100f)

        val ctx1 = RenderContext()
        root.render(ctx1)
        val ctx2 = RenderContext()
        root.render(ctx2)

        assertEquals(ctx1.commands.size, ctx2.commands.size)
        val a = pose(ctx1.commands.first())
        val b = pose(ctx2.commands.first())
        assertEquals(a.m30(), b.m30(), 1e-4f)
        assertEquals(a.m31(), b.m31(), 1e-4f)
        assertEquals(1, box.renderInternalCount, "静态内容应回放自身缓存, 不重录")
    }

    @Test
    fun `static replay at non-identity origin keeps world position`() {
        val root = FrameLayoutWidget()
        val box = OwnContentContainer()
        root.addChild("box", box)
        root.measureAndLayout(100f, 100f)
        box.layout(30f, 40f, 80f, 90f)

        val ctx1 = RenderContext()
        root.render(ctx1)
        assertEquals(30f, pose(ctx1.commands.first()).m30(), 1e-4f)
        assertEquals(40f, pose(ctx1.commands.first()).m31(), 1e-4f)

        val ctx2 = RenderContext()
        root.render(ctx2)
        assertEquals(30f, pose(ctx2.commands.first()).m30(), 1e-4f, "fast path 不应把世界位姿丢成左上角")
        assertEquals(40f, pose(ctx2.commands.first()).m31(), 1e-4f)
        assertEquals(1, box.renderInternalCount)
    }

    @Test
    fun `translation recomposes cached own content without re-recording`() {
        val root = FrameLayoutWidget()
        val box = OwnContentContainer()
        root.addChild("box", box)
        root.measureAndLayout(100f, 100f)

        val ctx1 = RenderContext()
        root.render(ctx1)
        assertEquals(0f, pose(ctx1.commands.first()).m30(), 1e-4f)
        assertEquals(0f, pose(ctx1.commands.first()).m31(), 1e-4f)

        box.translationX = 10f
        box.translationY = 20f

        val ctx2 = RenderContext()
        root.render(ctx2)
        val moved = pose(ctx2.commands.first())
        assertEquals(10f, moved.m30(), 1e-4f, "translation 应通过位姿重组反映")
        assertEquals(20f, moved.m31(), 1e-4f)
        assertEquals(1, box.renderInternalCount, "translation 不应触发重录 (RenderNode 重组)")
    }

    @Test
    fun `nested translation renders at correct positions`() {
        val root = FrameLayoutWidget()
        val outer = FrameLayoutWidget()
        val inner = FrameLayoutWidget()
        inner.addChild("fill", Fill())
        outer.addChild("inner", inner)
        root.addChild("outer", outer)
        root.measureAndLayout(100f, 100f)

        val ctx1 = RenderContext()
        root.render(ctx1)
        assertEquals(0f, pose(ctx1.commands.first()).m30(), 1e-4f)

        outer.translationX = 30f
        inner.translationY = 40f

        val ctx2 = RenderContext()
        root.render(ctx2)
        val p = pose(ctx2.commands.first())
        assertEquals(30f, p.m30(), 1e-4f)
        assertEquals(40f, p.m31(), 1e-4f)
    }

    @Test
    fun `alpha change recomposes via alphaMul without re-recording`() {
        val root = FrameLayoutWidget()
        val box = OwnContentContainer()
        root.addChild("box", box)
        root.measureAndLayout(100f, 100f)

        val ctx1 = RenderContext()
        root.render(ctx1)
        assertEquals(1, box.renderInternalCount)
        assertEquals(1f, ctx1.commands.first().alphaMul, 1e-4f)

        box.alpha = 0.5f
        val ctx2 = RenderContext()
        root.render(ctx2)
        assertEquals(1, box.renderInternalCount, "alpha 变化应通过 alphaMul 校正, 不重录")
        assertEquals(0.5f, ctx2.commands.first().alphaMul, 1e-4f, "校正乘子 = 当前累积 alpha / 录制累积 alpha")
    }

    @Test
    fun `nested alpha change corrects all cached levels`() {
        val root = FrameLayoutWidget()
        val outer = OwnContentContainer()
        val inner = OwnContentContainer()
        inner.addChild("fill", Fill())
        outer.addChild("inner", inner)
        root.addChild("outer", outer)
        root.measureAndLayout(100f, 100f)

        val ctx1 = RenderContext()
        root.render(ctx1)
        assertEquals(1, outer.renderInternalCount)
        assertEquals(1, inner.renderInternalCount)

        outer.alpha = 0.5f
        val ctx2 = RenderContext()
        root.render(ctx2)
        assertEquals(1, outer.renderInternalCount, "外层 alpha 变化不应重录外层自身")
        assertEquals(1, inner.renderInternalCount, "外层 alpha 变化不应重录内层")
        assertEquals(0.5f, ctx2.commands.first().alphaMul, 1e-4f)
    }

    @Test
    fun `scale recomposes cached own content without re-recording`() {
        val root = FrameLayoutWidget()
        val box = OwnContentContainer()
        root.addChild("box", box)
        root.measureAndLayout(100f, 100f)

        val ctx1 = RenderContext()
        root.render(ctx1)
        assertEquals(1f, pose(ctx1.commands.first()).m00(), 1e-4f)

        box.scaleX = 2f
        box.scaleY = 2f

        val ctx2 = RenderContext()
        root.render(ctx2)
        val scaled = pose(ctx2.commands.first())
        assertEquals(2f, scaled.m00(), 1e-4f, "scale 应通过位姿重组反映")
        assertEquals(2f, scaled.m11(), 1e-4f)
        assertEquals(1, box.renderInternalCount, "scale 不应触发重录 (RenderNode 重组)")
    }

    @Test
    fun `rotation recomposes cached own content without re-recording`() {
        val root = FrameLayoutWidget()
        val box = OwnContentContainer()
        root.addChild("box", box)
        root.measureAndLayout(100f, 100f)

        val ctx1 = RenderContext()
        root.render(ctx1)
        assertEquals(0f, pose(ctx1.commands.first()).m01(), 1e-4f)

        box.rotation = 90f

        val ctx2 = RenderContext()
        root.render(ctx2)
        val rotated = pose(ctx2.commands.first())
        assertTrue(Math.abs(rotated.m01()) > 0.5f, "rotation 应通过位姿重组反映 (m01 = ${rotated.m01()})")
        assertEquals(1, box.renderInternalCount, "rotation 不应触发重录 (RenderNode 重组)")
    }
}
