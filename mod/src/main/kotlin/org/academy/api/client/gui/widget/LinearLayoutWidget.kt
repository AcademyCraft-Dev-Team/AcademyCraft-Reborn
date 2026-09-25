package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import kotlin.math.max

open class LinearLayoutWidget : AbstractWidgetContainer() {
    var orientation: Orientation = Orientation.VERTICAL
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    var spacing: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    protected var weightSum: Float = -1.0f
    protected var gravity: Int = Gravity.START or Gravity.TOP
    private var totalLength = 0f

    override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams {
        if (orientation == Orientation.HORIZONTAL) {
            return LayoutParams().sizeMode(SizeMode.WRAP_CONTENT, SizeMode.WRAP_CONTENT)
        } else if (orientation == Orientation.VERTICAL) {
            return LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
        }
        return LayoutParams()
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        if (orientation == Orientation.VERTICAL) {
            measureVertical(widthMeasureSpec, heightMeasureSpec)
        } else {
            measureHorizontal(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun measureVertical(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        measureAlongAxis(false, widthMeasureSpec, heightMeasureSpec)
    }

    fun measureHorizontal(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        measureAlongAxis(true, widthMeasureSpec, heightMeasureSpec)
    }

    private fun measureAlongAxis(horizontal: Boolean, widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val mainSpec = if (horizontal) widthMeasureSpec else heightMeasureSpec
        val crossSpec = if (horizontal) heightMeasureSpec else widthMeasureSpec

        totalLength = 0f
        var maxCross = 0.0f
        var totalWeight = 0.0f
        var visibleChildCount = 0
        var hasMatchParentCross = false

        for (child in children.values) {
            if (!child.isVisible()) continue
            visibleChildCount++
            val lp = child.layoutParams as LayoutParams
            totalWeight += lp.weight
            if (lp.crossMode(horizontal) == SizeMode.MATCH_PARENT) {
                hasMatchParentCross = true
            }
        }

        val containerLp = layoutParams
        val crossMode = crossSpec.mode
        var allFillParent = true

        for (child in children.values) {
            if (!child.isVisible()) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val lp = child.layoutParams as LayoutParams
            totalLength += child.measuredMain(horizontal) + lp.mainStartMargin(horizontal) + lp.mainEndMargin(horizontal)
            maxCross = max(
                maxCross,
                child.measuredCross(horizontal) + lp.crossStartMargin(horizontal) + lp.crossEndMargin(horizontal)
            )
            allFillParent = allFillParent and (lp.mainMode(horizontal) == SizeMode.MATCH_PARENT)
        }

        if (visibleChildCount > 0) {
            totalLength += (visibleChildCount - 1) * spacing
        }
        totalLength += containerLp.mainPaddingStart(horizontal) + containerLp.mainPaddingEnd(horizontal)
        maxCross += containerLp.crossPaddingStart(horizontal) + containerLp.crossPaddingEnd(horizontal)

        val finalMain: Float = resolveSize(totalLength, mainSpec)
        val remainingSpace = finalMain - totalLength

        if (remainingSpace != 0f && totalWeight > 0) {
            val actualWeightSum = if (weightSum > 0) weightSum else totalWeight
            allFillParent = true

            for (child in children.values) {
                if (!child.isVisible()) continue
                val lp = child.layoutParams as LayoutParams
                if (lp.weight > 0) {
                    val share = remainingSpace * lp.weight / actualWeightSum
                    val childMain = child.measuredMain(horizontal) + share
                    val childMainSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, max(0f, childMain))
                    val childCrossSpec: MeasureSpec = getChildMeasureSpec(
                        crossSpec,
                        containerLp.crossPaddingStart(horizontal) + containerLp.crossPaddingEnd(horizontal) +
                                lp.crossStartMargin(horizontal) + lp.crossEndMargin(horizontal),
                        lp.crossSize(horizontal), lp.crossMode(horizontal)
                    )
                    child.measureWithAxis(horizontal, childMainSpec, childCrossSpec)
                }
                allFillParent = allFillParent and (lp.mainMode(horizontal) == SizeMode.MATCH_PARENT)
            }

            totalLength = 0f
            maxCross = 0f
            var finalVisibleChildCount = 0
            for (child in children.values) {
                if (!child.isVisible()) continue
                val lp = child.layoutParams as LayoutParams
                totalLength += child.measuredMain(horizontal) + lp.mainStartMargin(horizontal) + lp.mainEndMargin(
                    horizontal
                )
                maxCross = max(
                    maxCross,
                    child.measuredCross(horizontal) + lp.crossStartMargin(horizontal) + lp.crossEndMargin(horizontal)
                )
                finalVisibleChildCount++
            }

            if (finalVisibleChildCount > 0) {
                totalLength += (finalVisibleChildCount - 1) * spacing
            }
            totalLength += containerLp.mainPaddingStart(horizontal) + containerLp.mainPaddingEnd(horizontal)
            maxCross += containerLp.crossPaddingStart(horizontal) + containerLp.crossPaddingEnd(horizontal)
        }

        val finalCross: Float = resolveSize(maxCross, crossSpec)
        if (hasMatchParentCross && crossMode != MeasureSpec.Mode.UNSPECIFIED) {
            val innerCross =
                finalCross - containerLp.crossPaddingStart(horizontal) - containerLp.crossPaddingEnd(horizontal)
            for (child in children.values) {
                if (!child.isVisible()) continue
                val lp = child.layoutParams as LayoutParams
                if (lp.crossMode(horizontal) == SizeMode.MATCH_PARENT) {
                    val childTargetCross = innerCross - lp.crossStartMargin(horizontal) - lp.crossEndMargin(horizontal)
                    val childCrossSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, max(0f, childTargetCross))
                    val childMainSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, child.measuredMain(horizontal))
                    child.measureWithAxis(horizontal, childMainSpec, childCrossSpec)
                }
            }
        }

        if (horizontal) {
            setMeasuredDimension(finalMain, finalCross)
        } else {
            setMeasuredDimension(finalCross, finalMain)
        }
    }

    override fun onLayout() {
        if (orientation == Orientation.VERTICAL) {
            layoutVertical()
        } else {
            layoutHorizontal()
        }
    }

    fun layoutVertical() {
        val containerLp = layoutParams
        val paddingLeft = containerLp.paddingLeft
        val paddingTop = containerLp.paddingTop
        val paddingBottom = containerLp.paddingBottom
        val paddingRight = containerLp.paddingRight

        var currentY = paddingTop

        val majorGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK
        if (majorGravity == Gravity.BOTTOM) {
            currentY += height - paddingTop - paddingBottom - totalLength
        } else if (majorGravity == Gravity.CENTER_VERTICAL) {
            currentY += (height - paddingTop - paddingBottom - totalLength) / 2.0f
        }

        val availableWidth = width - paddingLeft - paddingRight
        var first = true

        for (child in children.values) {
            if (!child.isVisible()) continue

            val childLp = child.layoutParams as LayoutParams
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            var childGravity = childLp.gravity
            if (childGravity < 0) {
                childGravity = gravity
            }

            val horizontalGravity = childGravity and Gravity.HORIZONTAL_GRAVITY_MASK
            var childLeft = paddingLeft + childLp.marginLeft
            if (horizontalGravity == Gravity.CENTER_HORIZONTAL) {
                childLeft += (availableWidth - childWidth - childLp.marginLeft - childLp.marginRight) / 2.0f
            } else if (horizontalGravity == Gravity.RIGHT) {
                childLeft = width - paddingRight - childWidth - childLp.marginRight
            }

            if (!first) currentY += spacing
            val childTop = currentY + childLp.marginTop
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)

            currentY += childHeight + childLp.marginTop + childLp.marginBottom
            first = false
        }
    }

    fun layoutHorizontal() {
        val containerLp = layoutParams
        val paddingLeft = containerLp.paddingLeft
        val paddingTop = containerLp.paddingTop
        val paddingBottom = containerLp.paddingBottom
        val paddingRight = containerLp.paddingRight

        var currentX = paddingLeft

        val majorGravity = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        if (majorGravity == Gravity.RIGHT) {
            currentX += width - paddingLeft - paddingRight - totalLength
        } else if (majorGravity == Gravity.CENTER_HORIZONTAL) {
            currentX += (width - paddingLeft - paddingRight - totalLength) / 2.0f
        }

        val availableHeight = height - paddingTop - paddingBottom
        var first = true

        for (child in children.values) {
            if (!child.isVisible()) continue
            if (!first) currentX += spacing

            val childLp = child.layoutParams as LayoutParams
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            var childGravity = childLp.gravity
            if (childGravity < 0) {
                childGravity = gravity
            }

            val verticalGravity = childGravity and Gravity.VERTICAL_GRAVITY_MASK
            var childTop = paddingTop + childLp.marginTop
            if (verticalGravity == Gravity.CENTER_VERTICAL) {
                childTop += (availableHeight - childHeight - childLp.marginTop - childLp.marginBottom) / 2.0f
            } else if (verticalGravity == Gravity.BOTTOM) {
                childTop = height - paddingBottom - childHeight - childLp.marginBottom
            }

            val childLeft = currentX + childLp.marginLeft
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)

            currentX += childWidth + childLp.marginLeft + childLp.marginRight
            first = false
        }
    }

    fun setGravity(gravity: Int): LinearLayoutWidget {
        if (this.gravity != gravity) {
            this.gravity = gravity
            requestLayout()
        }
        return this
    }

    fun getLayoutGravity(): Int = gravity

    fun setWeightSum(weightSum: Float): LinearLayoutWidget {
        if (this.weightSum != weightSum) {
            this.weightSum = weightSum
            requestLayout()
        }
        return this
    }

    fun getLayoutWeightSum(): Float = weightSum

    class LayoutParams : WidgetContainer.LayoutParams {
        var weight: Float = 0f

        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source) {
            if (source is LayoutParams) {
                weight = source.weight
            }
        }

        fun weight(weight: Float): LayoutParams {
            this.weight = weight
            return this
        }
    }
}

private fun WidgetContainer.LayoutParams.mainMode(horizontal: Boolean): SizeMode =
    if (horizontal) widthMode else heightMode

private fun WidgetContainer.LayoutParams.crossMode(horizontal: Boolean): SizeMode =
    if (horizontal) heightMode else widthMode

private fun WidgetContainer.LayoutParams.crossSize(horizontal: Boolean): Float =
    if (horizontal) height else width

private fun WidgetContainer.LayoutParams.mainStartMargin(horizontal: Boolean): Float =
    if (horizontal) marginLeft else marginTop

private fun WidgetContainer.LayoutParams.mainEndMargin(horizontal: Boolean): Float =
    if (horizontal) marginRight else marginBottom

private fun WidgetContainer.LayoutParams.crossStartMargin(horizontal: Boolean): Float =
    if (horizontal) marginTop else marginLeft

private fun WidgetContainer.LayoutParams.crossEndMargin(horizontal: Boolean): Float =
    if (horizontal) marginBottom else marginRight

private fun WidgetContainer.LayoutParams.mainPaddingStart(horizontal: Boolean): Float =
    if (horizontal) paddingLeft else paddingTop

private fun WidgetContainer.LayoutParams.mainPaddingEnd(horizontal: Boolean): Float =
    if (horizontal) paddingRight else paddingBottom

private fun WidgetContainer.LayoutParams.crossPaddingStart(horizontal: Boolean): Float =
    if (horizontal) paddingTop else paddingLeft

private fun WidgetContainer.LayoutParams.crossPaddingEnd(horizontal: Boolean): Float =
    if (horizontal) paddingBottom else paddingRight

private fun Widget.measuredMain(horizontal: Boolean): Float =
    if (horizontal) measuredWidth else measuredHeight

private fun Widget.measuredCross(horizontal: Boolean): Float =
    if (horizontal) measuredHeight else measuredWidth

private fun Widget.measureWithAxis(horizontal: Boolean, mainSpec: MeasureSpec, crossSpec: MeasureSpec) {
    if (horizontal) {
        measure(mainSpec, crossSpec)
    } else {
        measure(crossSpec, mainSpec)
    }
}
