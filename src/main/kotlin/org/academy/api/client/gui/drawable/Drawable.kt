package org.academy.api.client.gui.drawable

import net.minecraft.util.ARGB
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.widget.Widget

interface Drawable {
    fun draw(context: Canvas, widget: Widget)
}

internal inline fun drawPaddedContent(
    context: Canvas,
    widget: Widget,
    tintColor: Int,
    body: (
        context: Canvas,
        width: Float,
        height: Float,
        r: Float,
        g: Float,
        b: Float,
        alpha: Float
    ) -> Unit
) {
    val lp = widget.layoutParams
    val contentWidth = widget.width - lp.paddingLeft - lp.paddingRight
    val contentHeight = widget.height - lp.paddingTop - lp.paddingBottom
    if (contentWidth <= 0 || contentHeight <= 0) return

    val alpha = (ARGB.alpha(tintColor) / 255.0f) * widget.getAbsoluteAlpha()
    if (alpha <= 0) return

    context.pose().pushPose()
    context.pose().translate(lp.paddingLeft, lp.paddingTop)
    body(
        context,
        contentWidth,
        contentHeight,
        ARGB.red(tintColor) / 255.0f,
        ARGB.green(tintColor) / 255.0f,
        ARGB.blue(tintColor) / 255.0f,
        alpha
    )
    context.pose().popPose()
}
