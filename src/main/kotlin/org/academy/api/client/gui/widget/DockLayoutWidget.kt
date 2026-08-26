package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import kotlin.math.max

/**
 * 停靠布局喵. 子控件按添加顺序依次停靠到内容区边缘:
 * [Dock.START]/[Dock.TOP] 依次占用首部, [Dock.END]/[Dock.BOTTOM] 依次占用尾部,
 * [Dock.FILL] 占满剩余空间 (多个 FILL 平均分摊).
 */
open class DockLayoutWidget : AbstractWidgetContainer() {
    override fun generateDefaultLayoutParams(): LayoutParams {
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
        val hasHeight = heightMeasureSpec.mode != MeasureSpec.Mode.UNSPECIFIED
        val availW = max(0f, widthMeasureSpec.size - containerLp.paddingLeft - containerLp.paddingRight)
        val availH = max(0f, heightMeasureSpec.size - containerLp.paddingTop - containerLp.paddingBottom)

        var remainingW = availW
        var remainingH = availH
        var consumedW = 0f
        var consumedH = 0f
        var fillCount = 0

        for (child in children.values) {
            if (!child.isVisible()) continue
            val lp = child.layoutParams as LayoutParams
            if (lp.dock == Dock.FILL) {
                fillCount++
                continue
            }
            val edge = lp.dock
            val parentSpec = when (edge) {
                Dock.START, Dock.END -> MeasureSpec(MeasureSpec.Mode.AT_MOST, max(0f, remainingW))
                Dock.TOP, Dock.BOTTOM -> MeasureSpec(MeasureSpec.Mode.AT_MOST, max(0f, remainingH))
                else -> MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
            }
            val unconstrainedSpec = MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
            child.measure(
                getChildMeasureSpec(
                    if (edge == Dock.START || edge == Dock.END) parentSpec else unconstrainedSpec,
                    lp.marginLeft + lp.marginRight,
                    lp.width, lp.widthMode, lp.widthPercent
                ),
                getChildMeasureSpec(
                    if (edge == Dock.TOP || edge == Dock.BOTTOM) parentSpec else unconstrainedSpec,
                    lp.marginTop + lp.marginBottom,
                    lp.height, lp.heightMode, lp.heightPercent
                )
            )

            when (edge) {
                Dock.START, Dock.END -> {
                    remainingW = max(0f, remainingW - child.measuredWidth)
                    consumedW += child.measuredWidth
                    consumedH = max(consumedH, child.measuredHeight)
                }

                Dock.TOP, Dock.BOTTOM -> {
                    remainingH = max(0f, remainingH - child.measuredHeight)
                    consumedW = max(consumedW, child.measuredWidth)
                    consumedH += child.measuredHeight
                }

                else -> {}
            }
        }

        if (fillCount > 0) {
            val fillW = if (hasWidth) remainingW / fillCount else remainingW
            val fillH = if (hasHeight) remainingH / fillCount else remainingH
            for (child in children.values) {
                if (!child.isVisible()) continue
                if ((child.layoutParams as LayoutParams).dock == Dock.FILL) {
                    val lp = child.layoutParams
                    val fillWidthSpec = if (hasWidth)
                        getChildMeasureSpec(
                            MeasureSpec(MeasureSpec.Mode.AT_MOST, max(0f, fillW)),
                            lp.marginLeft + lp.marginRight,
                            lp.width,
                            lp.widthMode,
                            lp.widthPercent
                        )
                    else MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
                    val fillHeightSpec = if (hasHeight)
                        getChildMeasureSpec(
                            MeasureSpec(MeasureSpec.Mode.AT_MOST, max(0f, fillH)),
                            lp.marginTop + lp.marginBottom,
                            lp.height,
                            lp.heightMode,
                            lp.heightPercent
                        )
                    else MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
                    child.measure(fillWidthSpec, fillHeightSpec)
                    consumedW = max(consumedW, child.measuredWidth)
                    consumedH = max(consumedH, child.measuredHeight)
                }
            }
        }

        setMeasuredDimension(
            resolveSize(consumedW + containerLp.paddingLeft + containerLp.paddingRight, widthMeasureSpec),
            resolveSize(consumedH + containerLp.paddingTop + containerLp.paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout() {
        val containerLp = layoutParams
        var left = containerLp.paddingLeft
        var top = containerLp.paddingTop
        var right = max(left, width - containerLp.paddingRight)
        var bottom = max(top, height - containerLp.paddingBottom)

        for (child in children.values) {
            if (!child.isVisible()) continue
            val lp = child.layoutParams as LayoutParams
            val childW = child.measuredWidth
            val childH = child.measuredHeight

            when (lp.dock) {
                Dock.START -> {
                    child.layout(left, top, left + childW, bottom)
                    left += childW
                }

                Dock.TOP -> {
                    child.layout(left, top, right, top + childH)
                    top += childH
                }

                Dock.END -> {
                    child.layout(right - childW, top, right, bottom)
                    right -= childW
                }

                Dock.BOTTOM -> {
                    child.layout(left, bottom - childH, right, bottom)
                    bottom -= childH
                }

                Dock.FILL -> {
                    child.layout(left, top, right, bottom)
                }
            }
        }
    }

    enum class Dock {
        START, TOP, END, BOTTOM, FILL
    }

    class LayoutParams : WidgetContainer.LayoutParams {
        var dock: Dock = Dock.FILL

        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source) {
            if (source is LayoutParams) {
                dock = source.dock
            }
        }

        fun dock(dock: Dock): LayoutParams {
            this.dock = dock
            return this
        }
    }
}
