package org.academy.desktop.widgets

import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.widget.AbstractWidget
import org.academy.api.client.gui.widget.Widget

/**
 * Draws a border rectangle around the widget returned by [target], in the
 * coordinate space of this widget's parent. Add it as a sibling of the preview
 * content to highlight the current selection.
 */
class SelectionBorderWidget(private val target: () -> Widget?) : AbstractWidget() {
    override fun renderInternal(context: RenderContext) {
        val t = target() ?: return
        val host = parent ?: return
        val dx = t.getAbsoluteX() - host.getAbsoluteX()
        val dy = t.getAbsoluteY() - host.getAbsoluteY()
        val thickness = 2f
        val r = 0x2D / 255f
        val g = 0x6A / 255f
        val b = 0x9F / 255f
        val a = 0.9f
        context.pose().pushPose()
        context.pose().translate(dx, dy)
        context.submit(FillRectDrawCommand(t.width, thickness, r, g, b, a))
        context.pose().pushPose()
        context.pose().translate(0f, t.height - thickness)
        context.submit(FillRectDrawCommand(t.width, thickness, r, g, b, a))
        context.pose().popPose()
        context.submit(FillRectDrawCommand(thickness, t.height, r, g, b, a))
        context.pose().pushPose()
        context.pose().translate(t.width - thickness, 0f)
        context.submit(FillRectDrawCommand(thickness, t.height, r, g, b, a))
        context.pose().popPose()
        context.pose().popPose()
    }
}
