package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import kotlin.math.max

/**
 * 宽高比约束布局喵. 唯一子控件在可用空间内保持 [aspectRatio] (宽/高),
 * 等比缩放至最大适配尺寸后铺满整个容器.
 */
open class AspectLayoutWidget(aspectRatio: Float = 1f) : AbstractWidgetContainer() {
    var aspectRatio: Float = aspectRatio
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    private var content: Widget? = null

    override fun addChild(name: String, child: Widget) {
        check(content == null) { "AspectLayoutWidget can host only one direct child." }
        content = child
        super.addChild(name, child)
    }

    override fun removeChild(name: String) {
        if (content != null && content!!.name == name) {
            super.removeChild(name)
            content = null
        }
    }

    override fun clearChildren() {
        content = null
        super.clearChildren()
    }

    override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams {
        return LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val child = content
        val containerLp = layoutParams
        if (child == null || !child.isVisible()) {
            setMeasuredDimension(
                resolveSize(containerLp.paddingLeft + containerLp.paddingRight, widthMeasureSpec),
                resolveSize(containerLp.paddingTop + containerLp.paddingBottom, heightMeasureSpec)
            )
            return
        }

        child.measure(MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f), MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f))

        val hasWidth = widthMeasureSpec.mode != MeasureSpec.Mode.UNSPECIFIED
        val hasHeight = heightMeasureSpec.mode != MeasureSpec.Mode.UNSPECIFIED
        val availW = max(0f, widthMeasureSpec.size - containerLp.paddingLeft - containerLp.paddingRight)
        val availH = max(0f, heightMeasureSpec.size - containerLp.paddingTop - containerLp.paddingBottom)

        val ratio = max(0.0001f, aspectRatio)

        var targetW = child.measuredWidth
        var targetH = child.measuredHeight
        if (ratio > 0f) {
            if (hasWidth && availW > 0f) {
                targetW = availW
                targetH = targetW / ratio
            } else if (hasHeight && availH > 0f) {
                targetH = availH
                targetW = targetH * ratio
            } else {
                targetW = child.measuredWidth
                targetH = child.measuredHeight
                val currentRatio = if (targetH > 0f) targetW / targetH else ratio
                if (currentRatio > ratio) {
                    targetW = targetH * ratio
                } else {
                    targetH = targetW / ratio
                }
            }
            if (hasHeight && availH > 0f && targetH > availH) {
                targetH = availH
                targetW = targetH * ratio
            }
            if (hasWidth && availW > 0f && targetW > availW) {
                targetW = availW
                targetH = targetW / ratio
            }
            targetW = max(0f, targetW)
            targetH = max(0f, targetH)
        }

        child.measure(MeasureSpec(MeasureSpec.Mode.EXACTLY, targetW), MeasureSpec(MeasureSpec.Mode.EXACTLY, targetH))

        setMeasuredDimension(
            resolveSize(targetW + containerLp.paddingLeft + containerLp.paddingRight, widthMeasureSpec),
            resolveSize(targetH + containerLp.paddingTop + containerLp.paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout() {
        val child = content
        if (child == null || !child.isVisible()) return
        val containerLp = layoutParams
        val childWidth = child.measuredWidth
        val childHeight = child.measuredHeight
        val left =
            containerLp.paddingLeft + (width - containerLp.paddingLeft - containerLp.paddingRight - childWidth) / 2.0f
        val top =
            containerLp.paddingTop + (height - containerLp.paddingTop - containerLp.paddingBottom - childHeight) / 2.0f
        child.layout(left, top, left + childWidth, top + childHeight)
    }

    class LayoutParams : WidgetContainer.LayoutParams {
        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source)
    }
}
