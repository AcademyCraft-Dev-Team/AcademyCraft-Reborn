package org.academy.api.client.gui.drawable

import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.widget.Widget

class StateListDrawable : Drawable {
    private data class StatePair(val mask: Int, val drawable: Drawable?)

    private val stateList: MutableList<StatePair> = ArrayList<StatePair>()
    private var defaultDrawable: Drawable? = null

    override fun draw(context: RenderContext, widget: Widget) {
        val currentState = widget.getWidgetState()

        for (pair in stateList) {
            if ((currentState and pair.mask) == pair.mask) {
                pair.drawable?.draw(context, widget)
                return
            }
        }

        if (defaultDrawable != null) {
            defaultDrawable!!.draw(context, widget)
        }
    }

    fun addState(stateMask: Int, drawable: Drawable?) {
        stateList.add(StatePair(stateMask, drawable))
    }

    fun setDefault(drawable: Drawable?) {
        defaultDrawable = drawable
    }
}