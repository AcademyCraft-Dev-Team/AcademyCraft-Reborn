package org.academy.api.client.gui.widget

import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.Drawable
import org.academy.api.client.gui.render.RenderContext

class FillWidget(color: Int) : AbstractWidget() {
    override var background: Drawable? = null
        set(background) {
            if (background is ColorDrawable) field = background
        }

    init {
        background = ColorDrawable(color)
    }

    override fun render(context: RenderContext) {
        if (!isVisible()) return
        super.render(context)
    }

    val color: Int
        get() {
            val bg = background
            if (bg is ColorDrawable) {
                return bg.color
            }
            return 0
        }

    fun setColor(color: Int): FillWidget {
        val bg = background
        if (bg is ColorDrawable) {
            bg.color = color
        } else {
            background = ColorDrawable(color)
        }
        return this
    }
}