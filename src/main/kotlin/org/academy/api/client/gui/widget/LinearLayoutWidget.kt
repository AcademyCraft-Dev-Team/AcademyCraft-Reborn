package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import kotlin.math.max

open class LinearLayoutWidget : AbstractWidgetContainer() {
    protected var orientation: Orientation = Orientation.VERTICAL
    protected var spacing: Float = 0f
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
        totalLength = 0f
        var maxWidth = 0.0f
        var totalWeight = 0.0f
        var visibleChildCount = 0
        var hasMatchParentWidth = false

        for (child in children.values) {
            if (!child.isVisible()) continue
            visibleChildCount++
            val lp = child.layoutParams as LayoutParams
            totalWeight += lp.weight
            if (lp.widthMode == SizeMode.MATCH_PARENT) {
                hasMatchParentWidth = true
            }
        }

        val containerLp = layoutParams
        val widthMode = widthMeasureSpec.mode
        var allFillParent = true

        for (child in children.values) {
            if (!child.isVisible()) continue
            val lp = child.layoutParams as LayoutParams
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            totalLength += child.measuredHeight + lp.marginTop + lp.marginBottom
            maxWidth = max(maxWidth, child.measuredWidth + lp.marginLeft + lp.marginRight)
            allFillParent = allFillParent and (lp.heightMode == SizeMode.MATCH_PARENT)
        }

        if (visibleChildCount > 0) {
            totalLength += (visibleChildCount - 1) * spacing
        }
        totalLength += containerLp.paddingTop + containerLp.paddingBottom
        maxWidth += containerLp.paddingLeft + containerLp.paddingRight

        val finalHeight: Float = resolveSize(totalLength, heightMeasureSpec)
        val remainingSpace = finalHeight - totalLength

        if (remainingSpace != 0f && totalWeight > 0) {
            val actualWeightSum = if (weightSum > 0) weightSum else totalWeight
            allFillParent = true

            for (child in children.values) {
                if (!child.isVisible()) continue
                val lp = child.layoutParams as LayoutParams
                if (lp.weight > 0) {
                    val share = remainingSpace * lp.weight / actualWeightSum
                    val childHeight = child.measuredHeight + share
                    val childHeightSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, max(0f, childHeight))
                    val childWidthSpec: MeasureSpec = getChildMeasureSpec(
                        widthMeasureSpec,
                        containerLp.paddingLeft + containerLp.paddingRight + lp.marginLeft + lp.marginRight,
                        lp.width, lp.widthMode
                    )
                    child.measure(childWidthSpec, childHeightSpec)
                }
                allFillParent = allFillParent and (lp.heightMode == SizeMode.MATCH_PARENT)
            }

            totalLength = 0f
            maxWidth = 0f
            var finalVisibleChildCount = 0
            for (child in children.values) {
                if (!child.isVisible()) continue
                val lp = child.layoutParams as LayoutParams
                totalLength += child.measuredHeight + lp.marginTop + lp.marginBottom
                maxWidth = max(maxWidth, child.measuredWidth + lp.marginLeft + lp.marginRight)
                finalVisibleChildCount++
            }

            if (finalVisibleChildCount > 0) {
                totalLength += (finalVisibleChildCount - 1) * spacing
            }
            totalLength += containerLp.paddingTop + containerLp.paddingBottom
            maxWidth += containerLp.paddingLeft + containerLp.paddingRight
        }

        val finalWidth: Float = resolveSize(maxWidth, widthMeasureSpec)
        if (hasMatchParentWidth && widthMode != MeasureSpec.Mode.UNSPECIFIED) {
            val innerWidth = finalWidth - containerLp.paddingLeft - containerLp.paddingRight
            for (child in children.values) {
                if (!child.isVisible()) continue
                val lp = child.layoutParams as LayoutParams
                if (lp.widthMode == SizeMode.MATCH_PARENT) {
                    val childTargetWidth = innerWidth - lp.marginLeft - lp.marginRight
                    val childWidthSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, max(0f, childTargetWidth))
                    val childHeightSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, child.measuredHeight)
                    child.measure(childWidthSpec, childHeightSpec)
                }
            }
        }

        setMeasuredDimension(finalWidth, finalHeight)
    }

    fun measureHorizontal(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        totalLength = 0f
        var maxHeight = 0.0f
        var totalWeight = 0.0f
        var visibleChildCount = 0
        var hasMatchParentHeight = false

        for (child in children.values) {
            if (!child.isVisible()) continue
            visibleChildCount++
            val lp = child.layoutParams as LayoutParams
            totalWeight += lp.weight
            if (lp.heightMode == SizeMode.MATCH_PARENT) {
                hasMatchParentHeight = true
            }
        }

        val containerLp = layoutParams
        val heightMode = heightMeasureSpec.mode
        var allFillParent = true

        for (child in children.values) {
            if (!child.isVisible()) continue

            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val lp = child.layoutParams as LayoutParams
            totalLength += child.measuredWidth + lp.marginLeft + lp.marginRight
            maxHeight = max(maxHeight, child.measuredHeight + lp.marginTop + lp.marginBottom)
            allFillParent = allFillParent and (lp.widthMode == SizeMode.MATCH_PARENT)
        }

        if (visibleChildCount > 0) {
            totalLength += (visibleChildCount - 1) * spacing
        }
        totalLength += containerLp.paddingLeft + containerLp.paddingRight
        maxHeight += containerLp.paddingTop + containerLp.paddingBottom

        val finalWidth: Float = resolveSize(totalLength, widthMeasureSpec)
        val remainingSpace = finalWidth - totalLength

        if (remainingSpace != 0f && totalWeight > 0) {
            val actualWeightSum = if (weightSum > 0) weightSum else totalWeight
            allFillParent = true

            for (child in children.values) {
                if (!child.isVisible()) continue
                val lp = child.layoutParams as LayoutParams
                if (lp.weight > 0) {
                    val share = remainingSpace * lp.weight / actualWeightSum
                    val childWidth = child.measuredWidth + share
                    val childWidthSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, max(0f, childWidth))
                    val childHeightSpec: MeasureSpec = getChildMeasureSpec(
                        heightMeasureSpec,
                        containerLp.paddingTop + containerLp.paddingBottom + lp.marginTop + lp.marginBottom,
                        lp.height, lp.heightMode
                    )
                    child.measure(childWidthSpec, childHeightSpec)
                }
                allFillParent = allFillParent and (lp.widthMode == SizeMode.MATCH_PARENT)
            }

            totalLength = 0f
            maxHeight = 0f
            var finalVisibleChildCount = 0
            for (child in children.values) {
                if (!child.isVisible()) continue
                val lp = child.layoutParams as LayoutParams
                totalLength += child.measuredWidth + lp.marginLeft + lp.marginRight
                maxHeight = max(maxHeight, child.measuredHeight + lp.marginTop + lp.marginBottom)
                finalVisibleChildCount++
            }

            if (finalVisibleChildCount > 0) {
                totalLength += (finalVisibleChildCount - 1) * spacing
            }
            totalLength += containerLp.paddingLeft + containerLp.paddingRight
            maxHeight += containerLp.paddingTop + containerLp.paddingBottom
        }

        val finalHeight: Float = resolveSize(maxHeight, heightMeasureSpec)
        if (hasMatchParentHeight && heightMode != MeasureSpec.Mode.UNSPECIFIED) {
            val innerHeight = finalHeight - containerLp.paddingTop - containerLp.paddingBottom
            for (child in children.values) {
                if (!child.isVisible()) continue
                val lp = child.layoutParams as LayoutParams
                if (lp.heightMode == SizeMode.MATCH_PARENT) {
                    val childTargetHeight = innerHeight - lp.marginTop - lp.marginBottom
                    val childHeightSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, max(0f, childTargetHeight))
                    val childWidthSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, child.measuredWidth)
                    child.measure(childWidthSpec, childHeightSpec)
                }
            }
        }

        setMeasuredDimension(finalWidth, finalHeight)
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

    fun setOrientation(orientation: Orientation): LinearLayoutWidget {
        if (this.orientation != orientation) {
            this.orientation = orientation
            requestLayout()
        }
        return this
    }

    fun setSpacing(spacing: Float): LinearLayoutWidget {
        if (this.spacing != spacing) {
            this.spacing = spacing
            requestLayout()
        }
        return this
    }

    fun setGravity(gravity: Int): LinearLayoutWidget {
        if (this.gravity != gravity) {
            this.gravity = gravity
            requestLayout()
        }
        return this
    }

    fun setWeightSum(weightSum: Float): LinearLayoutWidget {
        if (this.weightSum != weightSum) {
            this.weightSum = weightSum
            requestLayout()
        }
        return this
    }

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