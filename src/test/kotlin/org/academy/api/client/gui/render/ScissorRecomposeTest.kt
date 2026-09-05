package org.academy.api.client.gui.render

import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.widget.AbstractWidgetContainer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScissorRecomposeTest {
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

    /** 类似 ScrollPanel: render 时按当前状态重算 scissor 并包住子控件 (滚动通过位姿平移体现). */
    private class ScissorHost : AbstractWidgetContainer() {
        var clip = ScissorRect(0f, 0f, 50f, 50f)
        var childOffsetY = 0f

        override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams = FrameLayoutWidget.LayoutParams()
        override fun generateLayoutParams(p: WidgetContainer.LayoutParams): WidgetContainer.LayoutParams =
            FrameLayoutWidget.LayoutParams(p)

        override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean = p is FrameLayoutWidget.LayoutParams

        override fun render(context: RenderContext) {
            if (visibility != Widget.Visibility.VISIBLE) return
            context.pose().pushPose()
            context.alpha().push(alpha)
            context.enableScissor(clip)
            run {
                context.pose().pushPose()
                run {
                    context.pose().translate(0f, childOffsetY)
                    renderChildren(context)
                }
                context.pose().popPose()
            }
            context.disableScissor()
            context.alpha().pop()
            context.pose().popPose()
        }
    }

    private fun ScissorHost.measureAndLayout(w: Float, h: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, w),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, h)
        )
        layout(0f, 0f, w, h)
    }

    @Test
    fun `scissor change with unchanged pose restores current clip at replay`() {
        val host = ScissorHost()
        val box = OwnContentContainer()
        host.addChild("box", box)
        host.measureAndLayout(50f, 50f)

        val clip1 = ScissorRect(0f, 0f, 50f, 50f)
        val clip2 = ScissorRect(5f, 10f, 30f, 20f)

        host.clip = clip1
        val ctx1 = RenderContext()
        host.render(ctx1)
        assertEquals(1, box.renderInternalCount)
        assertEquals(clip1, ctx1.commands.first().scissorRect, "录制帧命令使用录制期祖先 scissor")

        host.clip = clip2
        val ctx2 = RenderContext()
        host.render(ctx2)
        assertEquals(1, box.renderInternalCount, "祖先裁剪变化不应重录子缓存")
        assertEquals(clip2, ctx2.commands.first().scissorRect, "回放应使用当前 scissor 栈重取")
    }

    @Test
    fun `scissor change with content scroll recomposes pose and clip`() {
        val host = ScissorHost()
        val box = OwnContentContainer()
        host.addChild("box", box)
        host.measureAndLayout(50f, 50f)

        val clip1 = ScissorRect(0f, 0f, 50f, 50f)
        val clip2 = ScissorRect(0f, 5f, 50f, 40f)

        host.clip = clip1
        host.childOffsetY = 0f
        val ctx1 = RenderContext()
        host.render(ctx1)
        assertEquals(1, box.renderInternalCount)
        assertEquals(0f, ctx1.commands.first().pose.pose().m31(), 1e-4f)

        host.clip = clip2
        host.childOffsetY = 20f
        val ctx2 = RenderContext()
        host.render(ctx2)
        assertEquals(1, box.renderInternalCount, "滚动 (位姿) 不应重录子缓存")
        assertEquals(20f, ctx2.commands.first().pose.pose().m31(), 1e-4f, "滚动通过位姿重组反映")
        assertEquals(clip2, ctx2.commands.first().scissorRect, "裁剪矩形随祖先滚动更新")
    }

    @Test
    fun `no scissor stack leaves cached commands unscissored`() {
        val root = FrameLayoutWidget()
        val box = OwnContentContainer()
        root.addChild("box", box)
        root.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 50f),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 50f)
        )
        root.layout(0f, 0f, 50f, 50f)

        root.render(RenderContext())
        val ctx2 = RenderContext()
        root.render(ctx2)
        assertEquals(null, ctx2.commands.first().scissorRect)
        assertEquals(1, box.renderInternalCount)
    }
}
