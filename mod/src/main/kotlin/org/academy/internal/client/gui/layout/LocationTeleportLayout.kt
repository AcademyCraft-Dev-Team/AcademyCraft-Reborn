package org.academy.internal.client.gui.layout

import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.widget.EmptyWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget

internal class LocationTeleportLayout(
    val root: FrameLayoutWidget,
    val panel: FrameLayoutWidget,
    val nameInput: EmptyWidget,
    val coordinates: EmptyWidget,
    val markCurrent: EmptyWidget,
    val addMark: EmptyWidget,
    val marks: EmptyWidget,
    val refresh: EmptyWidget,
    val done: EmptyWidget
)

internal fun buildLocationTeleportLayout(): LocationTeleportLayout {
    val root = FrameLayoutWidget()
    root.layoutParams = FrameLayoutWidget.LayoutParams()

    val panel = FrameLayoutWidget()
    root.addChild("panel", panel)
    panel.lp {
        size(PANEL_W, PANEL_H)
        gravity(Gravity.CENTER)
    }

    panel.blendQuad("panel_background") {
        matchWidth()
        matchHeight()
        marginLeft(1f)
        marginRight(1f)
        alpha = PANEL_ALPHA
        marginLeft = 4f
        marginTop = 4f
        marginRight = 4f
        marginBottom = 4f
        drawLine = false
        red = 0f
        green = 0f
        blue = 0f
    }

    panel.fill(PANEL_BORDER, "border_top") {
        size(PANEL_W - 8f, 1f)
        gravity(Gravity.TOP)
        marginLeft(4f)
    }
    panel.fill(PANEL_BORDER, "border_bottom") {
        size(PANEL_W - 8f, 1f)
        gravity(Gravity.BOTTOM)
        marginLeft(4f)
    }
    panel.fill(PANEL_BORDER, "border_left") {
        size(1f, PANEL_H - 8f)
        gravity(Gravity.LEFT)
        marginTop(4f)
    }
    panel.fill(PANEL_BORDER, "border_right") {
        size(1f, PANEL_H - 8f)
        gravity(Gravity.RIGHT)
        marginTop(4f)
    }
    panel.fill(PANEL_BORDER, "title_divider") {
        matchWidth()
        height(1f)
        margin(7f, 24f, 7f, 0f)
    }

    val nameInput = panel.empty("name_input") {
        size(PANEL_W - 24f, CONTROL_H)
        margin(12f, 32f, 0f, 0f)
    }
    val coordinates = panel.empty("coordinates") {
        size(PANEL_W - 24f, CONTROL_H)
        margin(12f, 58f, 0f, 0f)
    }
    val markCurrent = panel.empty("mark_current") {
        size(194f, CONTROL_H)
        margin(12f, 84f, 0f, 0f)
    }
    val addMark = panel.empty("add_mark") {
        size(194f, CONTROL_H)
        margin(214f, 84f, 0f, 0f)
    }
    val marks = panel.empty("marks") {
        size(PANEL_W - 24f, 94f)
        margin(12f, 108f, 0f, 0f)
    }
    val refresh = panel.empty("refresh") {
        size(194f, CONTROL_H)
        margin(12f, 208f, 0f, 0f)
    }
    val done = panel.empty("done") {
        size(194f, CONTROL_H)
        margin(214f, 208f, 0f, 0f)
    }

    return LocationTeleportLayout(
        root = root,
        panel = panel,
        nameInput = nameInput,
        coordinates = coordinates,
        markCurrent = markCurrent,
        addMark = addMark,
        marks = marks,
        refresh = refresh,
        done = done
    )
}

internal const val PANEL_W = 420f
internal const val PANEL_H = 236f
internal const val CONTROL_H = 20f
internal const val PANEL_ALPHA = 0.12f
internal const val PANEL_BORDER = 0xFF7680DE.toInt()
