package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import kotlin.math.max

open class WrapLayoutWidget : AbstractWidgetContainer() {
    var horizontalSpacing: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    var verticalSpacing: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams {
        return LayoutParams()
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val containerLp = layoutParams
        val window = measureWindow(widthMeasureSpec, heightMeasureSpec)
        val hasWidth = window.hasWidth
        val availW = window.availableWidth

        val line = WrapLine()
        var maxWidth = 0f

        for (child in children.values) {
            if (!child.isVisible()) continue
            val lp = child.layoutParams
            val parentSpec = if (hasWidth) MeasureSpec(MeasureSpec.Mode.AT_MOST, max(0f, availW - line.x))
            else MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
            val childSpec = getChildMeasureSpec(
                parentSpec,
                lp.marginLeft + lp.marginRight,
                lp.width, lp.widthMode, lp.widthPercent
            )
            val heightSpec = getChildMeasureSpec(
                MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f),
                lp.marginTop + lp.marginBottom,
                lp.height, lp.heightMode, lp.heightPercent
            )
            child.measure(childSpec, heightSpec)

            val childW = child.measuredWidth + lp.marginLeft + lp.marginRight
            val childH = child.measuredHeight + lp.marginTop + lp.marginBottom

            line.wrapIfNeeded(childW, availW, verticalSpacing)

            line.x += childW
            if (!line.firstInRow) line.x += horizontalSpacing
            maxWidth = max(maxWidth, line.x)
            line.rowHeight = max(line.rowHeight, childH)
            line.firstInRow = false
        }

        val totalHeight = line.y + line.rowHeight
        val totalWidth = if (hasWidth) availW else maxWidth

        setMeasuredDimension(
            resolveSize(totalWidth + containerLp.paddingLeft + containerLp.paddingRight, widthMeasureSpec),
            resolveSize(totalHeight + containerLp.paddingTop + containerLp.paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout() {
        val containerLp = layoutParams
        val availW = max(0f, width - containerLp.paddingLeft - containerLp.paddingRight)

        val line = WrapLine()

        for (child in children.values) {
            if (!child.isVisible()) continue
            val lp = child.layoutParams
            val childW = child.measuredWidth + lp.marginLeft + lp.marginRight
            val childH = child.measuredHeight + lp.marginTop + lp.marginBottom

            line.wrapIfNeeded(childW, availW, verticalSpacing)

            if (!line.firstInRow) line.x += horizontalSpacing
            val left = containerLp.paddingLeft + line.x + lp.marginLeft
            val top = containerLp.paddingTop + line.y + lp.marginTop
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)

            line.x += childW
            line.rowHeight = max(line.rowHeight, childH)
            line.firstInRow = false
        }
    }

    private class WrapLine {
        var x = 0f
        var y = 0f
        var rowHeight = 0f
        var firstInRow = true

        fun wrapIfNeeded(childWidth: Float, availableWidth: Float, verticalSpacing: Float) {
            if (!firstInRow && x + childWidth > availableWidth && availableWidth > 0f) {
                y += rowHeight + verticalSpacing
                x = 0f
                rowHeight = 0f
                firstInRow = true
            }
        }
    }

    class LayoutParams : WidgetContainer.LayoutParams {
        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source)
    }
}
