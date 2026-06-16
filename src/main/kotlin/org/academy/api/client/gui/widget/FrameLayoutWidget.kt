package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import kotlin.math.max

open class FrameLayoutWidget : AbstractWidgetContainer() {
    var measureAllChildren: Boolean = false
    private val matchParentChildren: MutableList<Widget> = ArrayList(1)

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams()
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun renderChildren(context: RenderContext) {
        context.drawOrder().push()
        run {
            for (child in children.values) {
                if (child.isVisible()) {
                    context.pose().pushPose()
                    run {
                        context.pose().translate(child.x, child.y)
                        context.pose().translate(child.translationX, child.translationY)
                        child.render(context)
                    }
                    context.pose().popPose()
                    context.drawOrder().advance()
                }
            }
        }
        context.drawOrder().pop()
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val measureMatchParentChildren =
            widthMeasureSpec.mode != MeasureSpec.Mode.EXACTLY ||
                    heightMeasureSpec.mode != MeasureSpec.Mode.EXACTLY
        matchParentChildren.clear()

        var maxHeight = 0.0f
        var maxWidth = 0.0f

        for (child in children.values) {
            if (measureAllChildren || child.isVisible()) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                val lp = child.layoutParams as LayoutParams
                maxWidth = max(
                    maxWidth,
                    child.measuredWidth + lp.marginLeft + lp.marginRight
                )
                maxHeight = max(
                    maxHeight,
                    child.measuredHeight + lp.marginTop + lp.marginBottom
                )
                if (measureMatchParentChildren) {
                    if (lp.widthMode == SizeMode.MATCH_PARENT ||
                        lp.heightMode == SizeMode.MATCH_PARENT
                    ) {
                        matchParentChildren.add(child)
                    }
                }
            }
        }

        val containerLp = layoutParams
        maxWidth += containerLp.paddingLeft + containerLp.paddingRight
        maxHeight += containerLp.paddingTop + containerLp.paddingBottom

        maxHeight = max(maxHeight, 0f)
        maxWidth = max(maxWidth, 0f)

        setMeasuredDimension(
            resolveSize(maxWidth, widthMeasureSpec),
            resolveSize(maxHeight, heightMeasureSpec)
        )

        val matchParentCount = matchParentChildren.size
        if (matchParentCount > 0) {
            for (child in matchParentChildren) {
                val lp = child.layoutParams as LayoutParams

                val childWidthMeasureSpec: MeasureSpec?
                if (lp.widthMode == SizeMode.MATCH_PARENT) {
                    val width = max(
                        0f, (measuredWidth
                                - containerLp.paddingLeft - containerLp.paddingRight
                                - lp.marginLeft - lp.marginRight)
                    )
                    childWidthMeasureSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, width)
                } else {
                    childWidthMeasureSpec = getChildMeasureSpec(
                        widthMeasureSpec,
                        containerLp.paddingLeft + containerLp.paddingRight +
                                lp.marginLeft + lp.marginRight,
                        lp.width, lp.widthMode
                    )
                }

                val childHeightMeasureSpec: MeasureSpec?
                if (lp.heightMode == SizeMode.MATCH_PARENT) {
                    val height = max(
                        0f, (measuredHeight
                                - containerLp.paddingTop - containerLp.paddingBottom
                                - lp.marginTop - lp.marginBottom)
                    )
                    childHeightMeasureSpec = MeasureSpec(MeasureSpec.Mode.EXACTLY, height)
                } else {
                    childHeightMeasureSpec = getChildMeasureSpec(
                        heightMeasureSpec,
                        containerLp.paddingTop + containerLp.paddingBottom +
                                lp.marginTop + lp.marginBottom,
                        lp.height, lp.heightMode
                    )
                }

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
            }
        }
    }

    override fun onLayout() {
        val containerLp = layoutParams
        val parentLeft = containerLp.paddingLeft
        val parentRight = width - containerLp.paddingRight
        val availableWidth = parentRight - parentLeft

        val parentTop = containerLp.paddingTop
        val parentBottom = height - containerLp.paddingBottom
        val availableHeight = parentBottom - parentTop

        for (child in children.values) {
            if (child.isVisible()) {
                val lp = child.layoutParams as LayoutParams

                val width = child.measuredWidth
                val height = child.measuredHeight

                var childLeft: Float
                var childTop: Float

                var gravity = lp.gravity
                if (gravity == -1) {
                    gravity = DEFAULT_CHILD_GRAVITY
                }

                val horizontalGravity = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
                val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK

                childLeft = parentLeft + lp.marginLeft
                if (horizontalGravity == Gravity.CENTER_HORIZONTAL) {
                    childLeft += (availableWidth - width - lp.marginLeft - lp.marginRight) / 2.0f
                } else if (horizontalGravity == Gravity.RIGHT) {
                    childLeft = parentRight - width - lp.marginRight
                }

                childTop = parentTop + lp.marginTop
                if (verticalGravity == Gravity.CENTER_VERTICAL) {
                    childTop += (availableHeight - height - lp.marginTop - lp.marginBottom) / 2.0f
                } else if (verticalGravity == Gravity.BOTTOM) {
                    childTop = parentBottom - height - lp.marginBottom
                }

                child.layout(childLeft, childTop, childLeft + width, childTop + height)
            }
        }
    }

    class LayoutParams : WidgetContainer.LayoutParams {
        constructor() {
            gravity = UNSPECIFIED_GRAVITY
            sizeMode(SizeMode.MATCH_PARENT)
        }

        constructor(source: WidgetContainer.LayoutParams) : super(source)

        companion object {
            const val UNSPECIFIED_GRAVITY: Int = -1
        }
    }

    companion object {
        private const val DEFAULT_CHILD_GRAVITY = Gravity.TOP or Gravity.START
    }
}