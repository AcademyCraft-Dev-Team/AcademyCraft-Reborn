package org.academy.api.client.gui.drawable

import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.widget.Widget

interface Drawable {
    fun draw(context: RenderContext, widget: Widget)
}