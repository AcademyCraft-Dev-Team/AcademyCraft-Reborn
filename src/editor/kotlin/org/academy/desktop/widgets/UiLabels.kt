package org.academy.desktop.widgets

import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.ButtonWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer

/**
 * A label that fills its container and centers its text on both axes — for
 * button contents and centered cells.
 */
fun centeredLabel(text: String, size: Float = 13f): LabelWidget = LabelWidget(text).apply {
    baseFontSize = size
    layoutParams = WidgetContainer.LayoutParams()
        .sizeMode(SizeMode.MATCH_PARENT)
        .gravity(Gravity.CENTER)
}

/**
 * A label whose text is vertically centered (and left-aligned) — for form rows
 * and list entries. Callers may still override layoutParams; keep gravity.
 */
fun vCenteredLabel(text: String, size: Float = 12f): LabelWidget = LabelWidget(text).apply {
    baseFontSize = size
    layoutParams = WidgetContainer.LayoutParams()
        .gravity(Gravity.CENTER_VERTICAL)
}

private const val HOVER_BG = 0xFF3A3A40.toInt()
private const val PRESSED_BG = 0xFF222226.toInt()

/**
 * Gives a [ButtonWidget] a hover/pressed tint so interactive controls give
 * visual feedback. [base] is the resting background (may be transparent).
 */
fun applyHoverState(button: ButtonWidget, base: Int = 0x00000000): ButtonWidget {
    val sld = StateListDrawable()
    sld.addState(Widget.PRESSED, ColorDrawable(PRESSED_BG))
    sld.addState(Widget.HOVERED, ColorDrawable(HOVER_BG))
    sld.setDefault(ColorDrawable(base))
    button.background = sld
    return button
}
