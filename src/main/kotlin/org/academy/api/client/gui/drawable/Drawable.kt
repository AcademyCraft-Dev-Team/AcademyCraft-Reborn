package org.academy.api.client.gui.drawable

import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.widget.Widget

interface Drawable {
    fun draw(context: Canvas, widget: Widget)
}
