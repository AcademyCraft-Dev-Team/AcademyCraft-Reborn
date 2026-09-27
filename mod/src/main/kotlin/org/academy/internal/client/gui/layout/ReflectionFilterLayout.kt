package org.academy.internal.client.gui.layout

import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.widget.EmptyWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget

internal class ReflectionFilterLayout(
    val root: FrameLayoutWidget,
    val panel: FrameLayoutWidget,
    val leftColumn: EmptyWidget,
    val middleColumn: EmptyWidget,
    val rightColumn: EmptyWidget
)

internal fun buildReflectionFilterLayout(compact: Boolean): ReflectionFilterLayout {
    val panelWidth = if (compact) 460f else 520f
    val panelHeight = if (compact) 238f else 260f
    val borderHeight = if (compact) 230f else 252f
    val columnHeight = if (compact) 208f else 230f
    val leftWidth = if (compact) 140f else 160f
    val rightMargin = if (compact) 246f else 266f
    val rightWidth = if (compact) 202f else 242f

    val root = FrameLayoutWidget()
    root.layoutParams = FrameLayoutWidget.LayoutParams()

    val panel = FrameLayoutWidget()
    root.addChild("panel", panel)
    panel.lp {
        size(panelWidth, panelHeight)
        gravity(Gravity.CENTER)
    }

    panel.blendQuad("panel_background") {
        matchWidth()
        matchHeight()
        marginLeft(1f)
        marginRight(1f)
        alpha = 0.5f
        marginLeft = 4f
        marginTop = 4f
        marginRight = 4f
        marginBottom = 4f
        drawLine = true
        red = 0f
        green = 0f
        blue = 0f
    }

    panel.fill(BORDER, "border_left") {
        size(1f, borderHeight)
        gravity(Gravity.LEFT)
        marginTop(4f)
    }
    panel.fill(BORDER, "border_right") {
        size(1f, borderHeight)
        gravity(Gravity.RIGHT)
        marginTop(4f)
    }
    panel.fill(BORDER, "title_divider") {
        matchWidth()
        height(1f)
        margin(7f, 24f, 7f, 0f)
    }

    val leftColumn = panel.empty("left_column") {
        size(leftWidth, columnHeight)
        margin(12f, 30f, 0f, 0f)
    }
    val middleColumn = panel.empty("middle_column") {
        size(74f, columnHeight)
        margin(if (compact) 162f else 182f, 30f, 0f, 0f)
    }
    val rightColumn = panel.empty("right_column") {
        size(rightWidth, columnHeight)
        margin(rightMargin, 30f, 0f, 0f)
    }

    return ReflectionFilterLayout(
        root = root,
        panel = panel,
        leftColumn = leftColumn,
        middleColumn = middleColumn,
        rightColumn = rightColumn
    )
}

private const val BORDER = 0x60FFFFFF
