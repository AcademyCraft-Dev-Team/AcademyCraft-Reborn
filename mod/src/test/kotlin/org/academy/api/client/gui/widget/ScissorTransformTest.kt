package org.academy.api.client.gui.widget

import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.ScissorRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 验证裁剪矩形会跟随控件的 scale 变换 (对标 Android 在当前矩阵下 clipRect).
 */
class ScissorTransformTest {
    private class SubmitProbe : AbstractWidget() {
        override fun renderInternal(context: Canvas) {
            context.submit(FillRectDrawCommand(4f, 4f, 1f, 1f, 1f, 1f))
        }
    }

    private fun Widget.measureAndLayout(width: Float, height: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, width),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, height)
        )
        layout(0f, 0f, width, height)
    }

    private fun firstScissor(ctx: Canvas): ScissorRect? = ctx.commands.first().scissorRect

    @Test
    fun `clipChildren scales scissor with container`() {
        val root = FrameLayoutWidget().apply {
            origin = 0f
            scaleX = 2f
            scaleY = 2f
        }
        root.addChild("probe", SubmitProbe())
        root.measureAndLayout(40f, 40f)

        val ctx = Canvas()
        root.render(ctx)

        assertEquals(ScissorRect(0f, 0f, 80f, 80f), firstScissor(ctx))
    }

    @Test
    fun `pager scales scissor`() {
        val pager = PagerLayoutWidget().apply {
            origin = 0f
            scaleX = 2f
            scaleY = 2f
        }
        pager.addChild("page", SubmitProbe())
        pager.measureAndLayout(40f, 40f)

        val ctx = Canvas()
        pager.render(ctx)

        assertEquals(ScissorRect(0f, 0f, 80f, 80f), firstScissor(ctx))
    }

    @Test
    fun `wheel picker scales scissor`() {
        val picker = WheelPickerWidget().apply {
            origin = 0f
            scaleX = 2f
            scaleY = 2f
            setItemHeight(15f)
            addChild("item", SubmitProbe())
        }
        picker.measureAndLayout(40f, 45f)

        val ctx = Canvas()
        picker.render(ctx)

        assertEquals(ScissorRect(0f, 0f, 80f, 90f), firstScissor(ctx))
    }

    @Test
    fun `nested clip fully outside ancestor yields empty scissor`() {
        val root = FrameLayoutWidget()
        val moving = FrameLayoutWidget().apply {
            translationX = 100f
            addChild("probe", SubmitProbe())
        }
        root.addChild("moving", moving)
        root.measureAndLayout(40f, 40f)

        val ctx = Canvas()
        root.render(ctx)

        assertEquals(ScissorRect.empty(), firstScissor(ctx))
    }
}
