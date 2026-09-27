package org.academy.api.client.gui.widget

/**
 * 堆叠布局喵 (FrameLayout v2). 子控件按 gravity 对齐, 以显式 [LayoutParams.zIndex]
 * 决定绘制与命中顺序 (zIndex 大的在上层). 同 zIndex 时保持添加顺序.
 */
open class StackLayoutWidget : FrameLayoutWidget() {
    override fun addChild(name: String, child: Widget) {
        super.addChild(name, child)
        reorderByZ()
    }

    private fun reorderByZ() {
        val sorted = ArrayList(protectedChildren.entries)
            .sortedBy { (it.value.layoutParams as? LayoutParams)?.zIndex ?: 0 }
        protectedChildren.clear()
        for ((key, value) in sorted) {
            protectedChildren[key] = value
        }
    }

    /** 把名为 [name] 的子控件移到最上层 (zIndex 设为当前最大 + 1). */
    fun bringToFront(name: String) {
        val child = children[name] ?: return
        val maxZ = children.values
            .mapNotNull { (it.layoutParams as? LayoutParams)?.zIndex }
            .maxOrNull() ?: 0
        val lp = child.layoutParams as? LayoutParams ?: LayoutParams().also { child.layoutParams = it }
        lp.zIndex = maxZ + 1
        reorderByZ()
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

    class LayoutParams : FrameLayoutWidget.LayoutParams {
        var zIndex: Int = 0

        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source) {
            if (source is LayoutParams) {
                zIndex = source.zIndex
            }
        }

        fun zIndex(zIndex: Int): LayoutParams {
            this.zIndex = zIndex
            return this
        }
    }
}
