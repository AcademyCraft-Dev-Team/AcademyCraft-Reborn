package org.academy.api.client.gui.drawable

import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.widget.Widget

class ColorDrawable(var color: Int) : Drawable {
    override fun draw(context: Canvas, widget: Widget) {
        drawPaddedContent(context, widget, color) { ctx, width, height, r, g, b, alpha ->
            ctx.submit(FillRectDrawCommand(width, height, r, g, b, alpha))
        }
    }
}
