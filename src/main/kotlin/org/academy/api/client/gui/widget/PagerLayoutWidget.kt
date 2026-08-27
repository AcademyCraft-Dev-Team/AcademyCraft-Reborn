package org.academy.api.client.gui.widget

import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.animation.TimeInterpolator
import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.render.ScissorRect

/**
 * 横向翻页容器 (pager).
 *
 * 子控件按加入顺序排成水平条带: 每页宽度 = 容器宽度, 一次只显示一页.
 * [switchToPage] 以 [pageSwitchDuration]/[pageSwitchInterpolator] 播放平移动画,
 * 渲染时用 scissor 裁剪到自身矩形, 溢出页面不可见.
 */
open class PagerLayoutWidget : FrameLayoutWidget() {
    private var pageOffset = 0f

    var currentPage: Int = 0
        private set

    val pageCount: Int get() = children.size

    var pageSwitchDuration: Long = 350L
    var pageSwitchInterpolator: TimeInterpolator = EasingFunctions.EASE_IN_OUT_SINE

    private var activeAnimator: Animator? = null

    /** 切换到 [index] 页并播放平移动画. */
    fun switchToPage(index: Int, animate: Boolean = true) {
        if (index < 0 || index >= children.size || index == currentPage) return
        activeAnimator?.cancel()
        currentPage = index
        val from = pageOffset
        val to = index.toFloat()
        if (!animate) {
            pageOffset = to
            invalidate()
            return
        }
        val anim = ObjectAnimator.ofFloat({ p ->
            pageOffset = p
            applyPageOffset()
            invalidate()
        }, from, to)
            .setDuration(pageSwitchDuration)
            .setInterpolator(pageSwitchInterpolator)
        activeAnimator = anim
        startAnimation(anim)
        invalidate()
    }

    /** 无动画跳到指定页 (初始布局). */
    fun jumpToPage(index: Int) {
        if (index < 0 || index >= children.size) return
        currentPage = index
        pageOffset = index.toFloat()
        applyPageOffset()
        invalidate()
    }

    /**
     * 序列化/编辑器专用: 不检查 children 是否就绪 (反序列化时子控件晚于属性应用) 设置当前页.
     * [render] 会对实际页数做钳制, 不会越界.
     */
    fun setCurrentPageUnchecked(index: Int) {
        currentPage = index.coerceAtLeast(0)
        pageOffset = index.coerceAtLeast(0).toFloat()
        applyPageOffset()
        invalidate()
    }

    /**
     * 将每页按当前 [pageOffset] 平移到对应屏幕位置.
     * 在 [onLayout] 与翻页动画中调用, 偏移持久化到子控件的 [translationX],
     * 使命中测试 (依赖 [Widget.getAbsoluteTranslationX]) 与渲染一致, 且空闲时无重绘.
     */
    private fun applyPageOffset() {
        var index = 0
        for (child in children.values) {
            val tx = (index - pageOffset) * width
            if (child.translationX != tx) child.translationX = tx
            index++
        }
    }

    override fun onLayout() {
        super.onLayout()
        applyPageOffset()
    }

    override fun render(context: RenderContext) {
        if (!isVisible()) return
        pageOffset = pageOffset.coerceIn(0f, (children.size - 1).coerceAtLeast(0).toFloat())
        applyPageOffset()
        val scissor = ScissorRect(
            getAbsoluteX() + getAbsoluteTranslationX(),
            getAbsoluteY() + getAbsoluteTranslationY(),
            width, height
        )
        context.enableScissor(scissor)
        super.render(context)
        context.disableScissor()
    }

    override fun dispatchEvent(event: InputEvent) {
        // 事件命中测试不感知 scissor 裁剪: 离屏页的控件按绝对坐标仍可被 isMouseOver 命中.
        // 这里把鼠标类事件裁剪到 Pager 可见矩形, 防止点击/滚动穿透到被裁掉的页面.
        if (event is MouseEvent && !isWithinVisibleBounds(event.x, event.y)) return
        if (event is ScrollEvent && !isWithinVisibleBounds(event.x, event.y)) return
        super.dispatchEvent(event)
    }

    private fun isWithinVisibleBounds(x: Double, y: Double): Boolean {
        val left = getAbsoluteX() + getAbsoluteTranslationX()
        val top = getAbsoluteY() + getAbsoluteTranslationY()
        return x >= left && y >= top && x < left + width && y < top + height
    }
}
