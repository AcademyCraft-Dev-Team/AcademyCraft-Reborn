package org.academy.api.client.gui.widget

import org.academy.api.client.gui.command.RoundedRectDrawCommand
import org.academy.api.client.gui.command.RoundedRectData
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoundedRectWidgetTest {

    private fun FrameLayoutWidget.measureAndLayout(width: Float, height: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, width),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, height)
        )
        layout(0f, 0f, width, height)
    }

    @Test
    fun `rounded rect submits one sdf command`() {
        val root = FrameLayoutWidget()
        val widget = RoundedRectWidget(0xFF335577.toInt(), 6f).apply {
            layoutParams = FrameLayoutWidget.LayoutParams().size(100f, 40f)
        }
        root.addChild("widget", widget)
        root.measureAndLayout(200f, 200f)

        val context = RenderContext()
        widget.render(context)

        assertEquals(1, context.commands.size)
        val command = context.commands[0].command
        assertTrue(command is RoundedRectDrawCommand)
        val payload = command.uniforms.first { it.name == "RoundedRectUniforms" }
        val data = payload.data as RoundedRectData
        assertEquals(100f, data.size.x)
        assertEquals(40f, data.size.y)
        assertEquals(6f, data.cornerRadius.x)
    }

    @Test
    fun `rounded rect colors are alpha scaled`() {
        val root = FrameLayoutWidget()
        val widget = RoundedRectWidget(0xFF335577.toInt(), 0f).apply {
            alpha = 0.5f
            layoutParams = FrameLayoutWidget.LayoutParams().size(10f, 10f)
        }
        root.addChild("widget", widget)
        root.measureAndLayout(100f, 100f)

        val context = RenderContext()
        widget.render(context)
        val command = context.commands[0].command
        val payload = command.uniforms.first()
        val data = payload.data as RoundedRectData
        // 0x335577 alpha=1, scaled by widget alpha 0.5
        assertEquals(0.5f, data.fillColor.w, 1e-4f)
    }

    @Test
    fun `hidden rounded rect submits nothing`() {
        val root = FrameLayoutWidget()
        val widget = RoundedRectWidget().apply {
            visibility = Widget.Visibility.INVISIBLE
            layoutParams = FrameLayoutWidget.LayoutParams().size(10f, 10f)
        }
        root.addChild("widget", widget)
        root.measureAndLayout(100f, 100f)

        val context = RenderContext()
        widget.render(context)
        assertTrue(context.commands.isEmpty())
    }
}
