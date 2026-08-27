package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import kotlin.math.max

/**
 * 流式换行布局喵. 子控件在可用的行宽内从左到右依次排列, 放不下时换行.
 * 行高取该行子控件最大高度, 换行间距由 [verticalSpacing] 控制.
 */
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
        val hasWidth = widthMeasureSpec.mode != MeasureSpec.Mode.UNSPECIFIED
        val availW = max(0f, widthMeasureSpec.size - containerLp.paddingLeft - containerLp.paddingRight)

        var x = 0f
        var y = 0f
        var rowHeight = 0f
        var maxWidth = 0f
        var firstInRow = true

        for (child in children.values) {
            if (!child.isVisible()) continue
            val lp = child.layoutParams
            val parentSpec = if (hasWidth) MeasureSpec(MeasureSpec.Mode.AT_MOST, max(0f, availW - x))
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

            if (!firstInRow && x + childW > availW && availW > 0f) {
                y += rowHeight + verticalSpacing
                x = 0f
                rowHeight = 0f
                firstInRow = true
            }

            x += childW
            if (!firstInRow) x += horizontalSpacing
            maxWidth = max(maxWidth, x)
            rowHeight = max(rowHeight, childH)
            firstInRow = false
        }

        val totalHeight = y + rowHeight
        val totalWidth = if (hasWidth) availW else maxWidth

        setMeasuredDimension(
            resolveSize(totalWidth + containerLp.paddingLeft + containerLp.paddingRight, widthMeasureSpec),
            resolveSize(totalHeight + containerLp.paddingTop + containerLp.paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout() {
        val containerLp = layoutParams
        val availW = max(0f, width - containerLp.paddingLeft - containerLp.paddingRight)

        var x = 0f
        var y = 0f
        var rowHeight = 0f
        var firstInRow = true

        for (child in children.values) {
            if (!child.isVisible()) continue
            val lp = child.layoutParams
            val childW = child.measuredWidth + lp.marginLeft + lp.marginRight
            val childH = child.measuredHeight + lp.marginTop + lp.marginBottom

            if (!firstInRow && x + childW > availW && availW > 0f) {
                y += rowHeight + verticalSpacing
                x = 0f
                rowHeight = 0f
                firstInRow = true
            }

            if (!firstInRow) x += horizontalSpacing
            val left = containerLp.paddingLeft + x + lp.marginLeft
            val top = containerLp.paddingTop + y + lp.marginTop
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)

            x += childW
            rowHeight = max(rowHeight, childH)
            firstInRow = false
        }
    }

    class LayoutParams : WidgetContainer.LayoutParams {
        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source)
    }
}
