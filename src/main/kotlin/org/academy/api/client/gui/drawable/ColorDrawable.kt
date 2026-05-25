package org.academy.api.client.gui.drawable

import net.minecraft.util.ARGB
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.widget.Widget

class ColorDrawable(var color: Int) : Drawable {
    override fun draw(context: RenderContext, widget: Widget) {
        val lp = widget.layoutParams
        val paddedWidth = widget.width - lp.paddingLeft - lp.paddingRight
        val paddedHeight = widget.height - lp.paddingTop - lp.paddingBottom

        if (paddedWidth <= 0 || paddedHeight <= 0) return

        val baseAlpha = ARGB.alpha(color) / 255.0f
        val finalAlpha = baseAlpha * widget.getAbsoluteAlpha()

        if (finalAlpha <= 0) return

        val r = ARGB.red(color) / 255.0f
        val g = ARGB.green(color) / 255.0f
        val b = ARGB.blue(color) / 255.0f

        context.pose().pushPose()
        context.pose().translate(lp.paddingLeft, lp.paddingTop, 0f)

        val command = FillRectDrawCommand(paddedWidth, paddedHeight, r, g, b, finalAlpha)
        context.submit(command)

        context.pose().popPose()
    }
}