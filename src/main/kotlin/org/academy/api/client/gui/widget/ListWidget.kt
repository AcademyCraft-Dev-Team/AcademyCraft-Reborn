package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import kotlin.math.max

/**
 * 虚拟化列表布局喵.
 *
 * 只实例化「可视窗口 ± [overscan]」内的条目视图, 滚动离开视野的视图回收进
 * [viewPool] 复用于新条目. 内容总高度 = 各条目高度之和, 使宿主滚动容器
 * (例如 [ScrollPanelWidget]) 能按完整内容滚动.
 *
 * 使用方式:
 * ```
 * val list = ListWidget<T>()
 * list.setItems(collection)
 * list.createItem = { position -> LinearLayoutWidget() }   // 可复用的视图骨架
 * list.bindItem = { view, item, position -> view.clearChildren(); view.label(...) }
 * list.itemHeight = { item, position -> 24f }               // 固定高度, 或
 * list.measureItem = { view, item, position -> /* 返回高度 */ }  // 可变高度
 * ```
 *
 * 条目高度解析顺序: 显式 [itemHeight] 优先; 否则调用 [measureItem]
 * (或对视图 measure 后取其 measuredHeight).
 */
open class ListWidget<T> : AbstractWidgetContainer() {
    /** 当前数据源. 设置后重建视图缓存并触发重布局. */
    var items: List<T> = emptyList()
        set(value) {
            if (field === value) return
            field = value
            onItemsChanged()
        }

    /** 条目间垂直间距. */
    var spacing: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /** 可视窗口外额外保留的条目数. */
    var overscan: Int = 3

    /** 固定条目高度提供器; 为 null 时使用 [measureItem]. */
    var itemHeight: ((item: T, position: Int) -> Float)? = null

    /** 可变条目高度提供器: 由调用方 measure 视图并返回高度. */
    var measureItem: ((view: WidgetContainer, item: T, position: Int) -> Float)? = null

    /** 创建可复用的条目视图骨架 (不包含内容). */
    var createItem: (position: Int) -> WidgetContainer = { _ -> LinearLayoutWidget() }

    /** 把 [item] 绑定到 [view] (先 clearChildren 再重建内容). */
    var bindItem: (view: WidgetContainer, item: T, position: Int) -> Unit = { _, _, _ -> }

    private val viewPool = ArrayDeque<WidgetContainer>()
    private val mountedViews = LinkedHashMap<Int, WidgetContainer>()
    private val itemHeights = HashMap<Int, Float>()
    private var totalHeight = 0f
    private var firstVisible = 0
    private var lastVisible = -1
    private var lastScrollY = 0f
    private var lastViewportHeight = -1f

    private fun onItemsChanged() {
        for (view in mountedViews.values) viewPool.addFirst(view)
        mountedViews.clear()
        itemHeights.clear()
        clearChildren()
        firstVisible = 0
        lastVisible = -1
        requestLayout()
        invalidate()
    }

    // ============ 测量 ============

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val containerLp = layoutParams
        val contentWidth = max(0f, widthMeasureSpec.size - containerLp.paddingLeft - containerLp.paddingRight)
        totalHeight = computeTotalHeight(contentWidth)

        val desiredWidth = resolveSize(contentWidth, widthMeasureSpec)
        val desiredHeight = totalHeight + containerLp.paddingTop + containerLp.paddingBottom
        setMeasuredDimension(desiredWidth, resolveSize(desiredHeight, heightMeasureSpec))
    }

    private fun computeTotalHeight(contentWidth: Float): Float {
        var sum = 0f
        val explicit = itemHeight
        for (i in items.indices) {
            val item = items[i]
            sum += if (explicit != null) {
                explicit(item, i)
            } else {
                itemHeights.getOrPut(i) { measureItemHeight(item, i, contentWidth) }
            }
        }
        if (items.size > 1) sum += spacing * (items.size - 1)
        return sum
    }

    private fun measureItemHeight(item: T, position: Int, contentWidth: Float): Float {
        val view = createItem(position)
        bindItem(view, item, position)
        val measured = measureItem?.invoke(view, item, position)
        if (measured != null) {
            viewPool.addFirst(view)
            return measured
        }
        view.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, contentWidth),
            MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
        )
        val height = view.measuredHeight
        viewPool.addFirst(view)
        return height
    }

    // ============ 布局与虚拟化 ============

    override fun onLayout() {
        super.onLayout()
        lastScrollY = scrollOffset()
        updateVisibleRange()
    }

    override fun tick() {
        super.tick()
        val scrollY = scrollOffset()
        val viewportHeight = viewportHeight()
        if (scrollY != lastScrollY || viewportHeight != lastViewportHeight) {
            lastScrollY = scrollY
            lastViewportHeight = viewportHeight
            updateVisibleRange()
        }
    }

    private fun scrollOffset(): Float {
        var y = 0f
        var current: WidgetContainer? = parent
        while (current != null) {
            y += current.scrollY
            current = current.parent
        }
        return y
    }

    private fun viewportHeight(): Float {
        var current: WidgetContainer? = parent
        while (current != null) {
            if (current is ScrollPanelWidget) {
                val lp = current.layoutParams
                return max(0f, current.height - lp.paddingTop - lp.paddingBottom)
            }
            current = current.parent
        }
        return height
    }

    private fun updateVisibleRange() {
        if (items.isEmpty()) {
            clearMounted()
            return
        }
        val viewport = viewportHeight()
        val scrollY = lastScrollY
        val start = scrollY - overscanHeight()
        val end = scrollY + viewport + overscanHeight()

        var first = indexAt(start).coerceIn(0, items.size - 1)
        var last = indexAt(end)
        if (last < first) last = first
        last = last.coerceIn(first, items.size - 1)

        // 回收离开视野的视图
        for (position in mountedViews.keys.toList()) {
            if (position < first || position > last) {
                val view = mountedViews.remove(position)!!
                removeChild(view.name)
                viewPool.addFirst(view)
            }
        }

        // 挂载新进入视野的视图
        for (position in first..last) {
            if (position in mountedViews) continue
            val view = viewPool.removeLastOrNull() ?: createItem(position)
            bindItem(view, items[position], position)
            val name = "item_$position"
            super.addChild(name, view)
            mountedViews[position] = view
            layoutMounted(position, view)
        }

        firstVisible = first
        lastVisible = last
    }

    private fun clearMounted() {
        for (view in mountedViews.values) viewPool.addFirst(view)
        mountedViews.clear()
        clearChildren()
    }

    private fun overscanHeight(): Float {
        if (items.isEmpty()) return 0f
        var h = 0f
        for (i in items.indices) {
            h += heightAt(i)
            if (i > 0) h += spacing
        }
        val avg = if (items.isNotEmpty()) h / items.size else 0f
        return avg * overscan
    }

    private fun layoutMounted(position: Int, view: WidgetContainer) {
        val containerLp = layoutParams
        val contentWidth = max(0f, width - containerLp.paddingLeft - containerLp.paddingRight)
        // bindItem rebuilds the view's children after it was last measured, so the
        // freshly-bound children are unmeasured. Measure the view against the list
        // content width before laying it out, or every mounted item renders empty.
        view.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, contentWidth),
            MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
        )
        val measuredHeight = max(0f, view.measuredHeight)
        itemHeights[position] = measuredHeight

        val left = containerLp.paddingLeft
        val right = max(left, width - containerLp.paddingRight)
        val top = containerLp.paddingTop + offsetAt(position)
        view.layout(left, top, right, top + measuredHeight)
    }

    private fun heightAt(position: Int): Float {
        val explicit = itemHeight
        val height = if (explicit != null) explicit(items[position], position) else itemHeights[position] ?: 0f
        return max(0f, height)
    }

    private fun offsetAt(position: Int): Float {
        var offset = 0f
        for (i in 0 until position) {
            offset += heightAt(i)
            offset += spacing
        }
        return offset
    }

    private fun indexAt(y: Float): Int {
        if (y <= 0f) return 0
        var offset = 0f
        for (i in items.indices) {
            val h = heightAt(i) + (if (i > 0) spacing else 0f)
            if (y < offset + h) return i
            offset += h
        }
        return items.size - 1
    }

    // ============ 覆盖: 所有子级均为虚拟化条目, 拒绝直接 addChild ============

    override fun addChild(name: String, child: Widget) {
        error("ListWidget children are virtualized; use createItem/bindItem instead of addChild.")
    }

    override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams {
        return LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is LayoutParams
    }

    class LayoutParams : WidgetContainer.LayoutParams {
        constructor()

        constructor(source: WidgetContainer.LayoutParams) : super(source)
    }
}
