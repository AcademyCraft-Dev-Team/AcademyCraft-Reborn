package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import kotlin.math.ceil
import kotlin.math.max

/**
 * 等分网格布局喵. 子控件按行主序填入 `columns` 列网格.
 *
 * - [rows] 为 0 时按子控件数量自动推导.
 * - [fill] 为 true 且父容器给出明确尺寸时, 单元格等分铺满容器.
 * - 子控件默认占满所在单元格 (等效 MATCH_PARENT).
 */
open class GridLayoutWidget : AbstractWidgetContainer() {
    var columns: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    var rows: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    var fill: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
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

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams()
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is LayoutParams
    }

    private fun visibleCount(): Int = children.values.count { it.isVisible() }

    private fun gridDimensions(count: Int): Pair<Int, Int> {
        val cols = max(1, columns)
        val rowCount = if (rows > 0) rows else ceil(count.toFloat() / cols).toInt()
        return cols to max(1, rowCount)
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val count = visibleCount()
        if (count == 0) {
            setMeasuredDimension(0f, 0f)
            return
        }
        val containerLp = layoutParams
        val (cols, rowCount) = gridDimensions(count)

        val hasWidth = widthMeasureSpec.mode != MeasureSpec.Mode.UNSPECIFIED
        val hasHeight = heightMeasureSpec.mode != MeasureSpec.Mode.UNSPECIFIED
        val availW = max(0f, widthMeasureSpec.size - containerLp.paddingLeft - containerLp.paddingRight)
        val availH = max(0f, heightMeasureSpec.size - containerLp.paddingTop - containerLp.paddingBottom)

        val cellW = if (hasWidth) availW / cols else -1f
        val cellH = if (hasHeight) availH / rowCount else -1f

        var maxCellW = 0f
        var maxCellH = 0f
        for (child in children.values) {
            if (!child.isVisible()) continue
            val lp = child.layoutParams
            val cellWidthSpec = if (cellW >= 0f)
                getChildMeasureSpec(
                    MeasureSpec(MeasureSpec.Mode.AT_MOST, cellW),
                    lp.marginLeft + lp.marginRight,
                    lp.width,
                    lp.widthMode,
                    lp.widthPercent
                )
            else MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
            val cellHeightSpec = if (cellH >= 0f)
                getChildMeasureSpec(
                    MeasureSpec(MeasureSpec.Mode.AT_MOST, cellH),
                    lp.marginTop + lp.marginBottom,
                    lp.height,
                    lp.heightMode,
                    lp.heightPercent
                )
            else MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
            child.measure(cellWidthSpec, cellHeightSpec)
            maxCellW = max(maxCellW, child.measuredWidth)
            maxCellH = max(maxCellH, child.measuredHeight)
        }

        val desiredWidth = cols * maxCellW + (cols - 1) * horizontalSpacing
        val desiredHeight = rowCount * maxCellH + (rowCount - 1) * verticalSpacing

        setMeasuredDimension(
            resolveSize(desiredWidth + containerLp.paddingLeft + containerLp.paddingRight, widthMeasureSpec),
            resolveSize(desiredHeight + containerLp.paddingTop + containerLp.paddingBottom, heightMeasureSpec)
        )

        if (fill && hasWidth && hasHeight) {
            reMeasureChildren(availW, availH, cols, rowCount)
        }
    }

    private fun reMeasureChildren(availW: Float, availH: Float, cols: Int, rowCount: Int) {
        val cellW = availW / cols
        val cellH = availH / rowCount
        for (child in children.values) {
            if (!child.isVisible()) continue
            child.measure(MeasureSpec(MeasureSpec.Mode.EXACTLY, cellW), MeasureSpec(MeasureSpec.Mode.EXACTLY, cellH))
        }
    }

    override fun onLayout() {
        val count = visibleCount()
        if (count == 0) return
        val containerLp = layoutParams
        val (cols, rowCount) = gridDimensions(count)

        val totalW = max(0f, width - containerLp.paddingLeft - containerLp.paddingRight)
        val totalH = max(0f, height - containerLp.paddingTop - containerLp.paddingBottom)
        val spacingX = (cols - 1) * horizontalSpacing
        val spacingY = (rowCount - 1) * verticalSpacing

        val cellW = if (fill && totalW > spacingX) (totalW - spacingX) / cols else totalW / cols
        val cellH = if (fill && totalH > spacingY) (totalH - spacingY) / rowCount else totalH / rowCount

        var index = 0
        for (child in children.values) {
            if (!child.isVisible()) continue
            val row = index / cols
            val col = index % cols
            val left = containerLp.paddingLeft + col * (cellW + horizontalSpacing)
            val top = containerLp.paddingTop + row * (cellH + verticalSpacing)
            val lp = child.layoutParams
            val childLeft = left + lp.marginLeft
            val childTop = top + lp.marginTop
            val childRight = left + cellW - lp.marginRight
            val childBottom = top + cellH - lp.marginBottom
            child.layout(childLeft, childTop, max(childLeft, childRight), max(childTop, childBottom))
            index++
        }
    }

    class LayoutParams : WidgetContainer.LayoutParams {
        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source)
    }
}
