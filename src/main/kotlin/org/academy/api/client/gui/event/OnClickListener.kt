package org.academy.api.client.gui.event

import org.academy.api.client.gui.widget.Widget

fun interface OnClickListener {
    fun onClick(source: Widget)
}