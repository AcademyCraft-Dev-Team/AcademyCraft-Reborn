package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import kotlin.math.max

/**
 * 布局 v2 的锚点约束容器喵.
 *
 * 每个子控件通过 [WidgetContainer.LayoutParams.anchorX]/[anchorY] 在内容区内定位:
 * - 点锚点: 子控件的边/中心按 `anchorX * (contentW - childW)` 摆放, `offset*` 叠加像素偏移.
 *   例: anchors(0f,0f) 左上, anchors(0.5f,0.5f) 居中, anchors(1f,1f) 右下.
 * - 拉伸: [WidgetContainer.LayoutParams.stretchX]/[stretchY] 为 true 时, 从 [WidgetContainer.LayoutParams.anchorX]
 *   拉伸到 [WidgetContainer.LayoutParams.anchorX2] (未设置则到内容区右/下边缘).
 *
 * 尺寸模式支持 [SizeMode.PERCENT] (相对父容器内容区的百分比).
 */
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
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            val availW = max(0f, contentWidth - lp.marginLeft - lp.marginRight)
            val availH = max(0f, contentHeight - lp.marginTop - lp.marginBottom)
            val baseLeft = contentLeft + lp.marginLeft
            val baseTop = contentTop + lp.marginTop

            var childLeft: Float
            var childTop: Float
            var layoutWidth = childWidth
            var layoutHeight = childHeight

            if (lp.stretchX) {
                val left = baseLeft + lp.anchorX * availW + lp.offsetX
                val right = if (lp.anchorX2 >= 0f) {
                    baseLeft + lp.anchorX2 * availW + lp.offsetX
                } else {
                    baseLeft + availW + lp.offsetX
                }
                childLeft = left
                layoutWidth = max(0f, right - left)
            } else {
                childLeft = baseLeft + lp.anchorX * (availW - childWidth) + lp.offsetX
            }

            if (lp.stretchY) {
                val top = baseTop + lp.anchorY * availH + lp.offsetY
                val bottom = if (lp.anchorY2 >= 0f) {
                    baseTop + lp.anchorY2 * availH + lp.offsetY
                } else {
                    baseTop + availH + lp.offsetY
                }
                childTop = top
                layoutHeight = max(0f, bottom - top)
            } else {
                childTop = baseTop + lp.anchorY * (availH - childHeight) + lp.offsetY
            }

            child.layout(childLeft, childTop, childLeft + layoutWidth, childTop + layoutHeight)
        }
    }

    class LayoutParams : WidgetContainer.LayoutParams {
        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source)
    }
}
