package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import kotlin.math.max

open class AnchorLayoutWidget : AbstractWidgetContainer() {
    private val matchChildren: MutableList<Widget> = ArrayList()

    override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams {
        return LayoutParams().sizeMode(SizeMode.WRAP_CONTENT, SizeMode.WRAP_CONTENT)
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val measureMatchChildren =
            widthMeasureSpec.mode != MeasureSpec.Mode.EXACTLY ||
                    heightMeasureSpec.mode != MeasureSpec.Mode.EXACTLY
        matchChildren.clear()

        var maxWidth = 0.0f
        var maxHeight = 0.0f

        for (child in children.values) {
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val lp = child.layoutParams
            maxWidth = max(maxWidth, child.measuredWidth + lp.marginLeft + lp.marginRight)
            maxHeight = max(maxHeight, child.measuredHeight + lp.marginTop + lp.marginBottom)
            if (measureMatchChildren &&
                (lp.stretchX || lp.stretchY ||
                        lp.widthMode == SizeMode.MATCH_PARENT || lp.heightMode == SizeMode.MATCH_PARENT ||
                        lp.widthMode == SizeMode.PERCENT || lp.heightMode == SizeMode.PERCENT)
            ) {
                matchChildren.add(child)
            }
        }

        val containerLp = layoutParams
        maxWidth += containerLp.paddingLeft + containerLp.paddingRight
        maxHeight += containerLp.paddingTop + containerLp.paddingBottom

        setMeasuredDimension(
            resolveSize(maxWidth, widthMeasureSpec),
            resolveSize(maxHeight, heightMeasureSpec)
        )

        if (matchChildren.isNotEmpty()) {
            val containerLp = layoutParams
            val parentW = max(0f, measuredWidth - containerLp.paddingLeft - containerLp.paddingRight)
            val parentH = max(0f, measuredHeight - containerLp.paddingTop - containerLp.paddingBottom)
            val parentWidthSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, parentW)
            val parentHeightSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, parentH)
            for (child in matchChildren) {
                val lp = child.layoutParams
                val childWidthMeasureSpec = when {
                    lp.stretchX -> MeasureSpec(
                        MeasureSpec.Mode.EXACTLY,
                        max(0f, parentW - lp.marginLeft - lp.marginRight)
                    )

                    else -> getChildMeasureSpec(
                        parentWidthSpec,
                        lp.marginLeft + lp.marginRight,
                        lp.width, lp.widthMode, lp.widthPercent
                    )
                }
                val childHeightMeasureSpec = when {
                    lp.stretchY -> MeasureSpec(
                        MeasureSpec.Mode.EXACTLY,
                        max(0f, parentH - lp.marginTop - lp.marginBottom)
                    )

                    else -> getChildMeasureSpec(
                        parentHeightSpec,
                        lp.marginTop + lp.marginBottom,
                        lp.height, lp.heightMode, lp.heightPercent
                    )
                }
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
            }
        }
    }

    override fun onLayout() {
        val containerLp = layoutParams
        val contentLeft = containerLp.paddingLeft
        val contentTop = containerLp.paddingTop
        val contentWidth = max(0f, width - containerLp.paddingLeft - containerLp.paddingRight)
        val contentHeight = max(0f, height - containerLp.paddingTop - containerLp.paddingBottom)

        for (child in children.values) {
            if (!child.isVisible()) continue
            val lp = child.layoutParams

            val availW = max(0f, contentWidth - lp.marginLeft - lp.marginRight)
            val availH = max(0f, contentHeight - lp.marginTop - lp.marginBottom)
            val baseLeft = contentLeft + lp.marginLeft
            val baseTop = contentTop + lp.marginTop

            val horizontal = resolveAxis(
                lp.stretchX, lp.anchorX, lp.anchorX2, lp.offsetX,
                baseLeft, availW, child.measuredWidth
            )
            val vertical = resolveAxis(
                lp.stretchY, lp.anchorY, lp.anchorY2, lp.offsetY,
                baseTop, availH, child.measuredHeight
            )

            child.layout(
                horizontal.start,
                vertical.start,
                horizontal.start + horizontal.extent,
                vertical.start + vertical.extent
            )
        }
    }

    private fun resolveAxis(
        stretch: Boolean,
        anchor: Float,
        anchor2: Float,
        offset: Float,
        base: Float,
        available: Float,
        childSize: Float
    ): AxisPlacement {
        if (!stretch) return AxisPlacement(base + anchor * (available - childSize) + offset, childSize)

        val start = base + anchor * available + offset
        val end = if (anchor2 >= 0f) {
            base + anchor2 * available + offset
        } else {
            base + available + offset
        }
        return AxisPlacement(start, max(0f, end - start))
    }

    private class AxisPlacement(val start: Float, val extent: Float)

    class LayoutParams : WidgetContainer.LayoutParams {
        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source)
    }
}
