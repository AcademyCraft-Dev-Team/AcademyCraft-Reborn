package org.academy.desktop.widgets

import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.ButtonWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget

/**
 * A 3x3 gravity picker plus a "Fill" option. Each cell maps to a concrete
 * [Gravity] bitmask value, so editing gravity never requires typing raw numbers.
 */
class GravityPickerWidget : LinearLayoutWidget() {
    private var selected: Int = Gravity.CENTER
    private var onChange: (Int) -> Unit = {}

    init {
        orientation = Orientation.VERTICAL
        spacing = 2f
    }

    fun setGravity(value: Int, handler: (Int) -> Unit) {
        this.selected = value
        this.onChange = handler
        clearChildren()

        val grid = listOf(
            listOf(Gravity.TOP_LEFT to "↖", Gravity.TOP to "↑", Gravity.TOP_RIGHT to "↗"),
            listOf(Gravity.CENTER_LEFT to "←", Gravity.CENTER to "·", Gravity.CENTER_RIGHT to "→"),
            listOf(Gravity.BOTTOM_LEFT to "↙", Gravity.BOTTOM to "↓", Gravity.BOTTOM_RIGHT to "↘")
        )
        for (row in grid) {
            val rowWidget = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = 2f
                layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED).height(20f)
            }
            for ((gravity, symbol) in row) {
                rowWidget.addChild(
                    "g$gravity",
                    ButtonWidget(centeredLabel(symbol, 11f)).apply {
                        layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).heightMode(SizeMode.MATCH_PARENT)
                        applyHoverState(this, if (selected == gravity) 0xFF2D6A9F.toInt() else 0x40202020)
                        onClickListener = OnClickListener {
                            selected = gravity
                            onChange(gravity)
                        }
                    }
                )
            }
            addChild("row_${row.first().first}", rowWidget)
        }

        addChild(
            "fill",
            ButtonWidget(centeredLabel("Fill", 11f)).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED).height(20f)
                applyHoverState(this, if (selected == Gravity.FILL) 0xFF2D6A9F.toInt() else 0x40202020)
                onClickListener = OnClickListener {
                    selected = Gravity.FILL
                    onChange(Gravity.FILL)
                }
            }
        )
    }
}
