package org.academy.api.client.gui.widget

import net.minecraft.util.Mth
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.util.Chase
import kotlin.math.max

open class ScrollPanelWidget(protected val orientation: Orientation? = Orientation.VERTICAL) :
    AbstractWidgetContainer() {
    protected var scrollTargetX: Float = 0f
    protected var scrollTargetY: Float = 0f
    protected var scrollSpeed: Float = 24f
    private var pendingScrollToEnd = false

    var content: Widget? = null
        private set

    private val scrollListeners: MutableList<() -> Unit> = ArrayList()

    fun addScrollChangeListener(listener: () -> Unit) {
        scrollListeners.add(listener)
    }

    private fun notifyScrollChanged() {
        if (scrollListeners.isEmpty()) return
        for (listener in scrollListeners) listener()
    }

    override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams {
        return FrameLayoutWidget.LayoutParams()
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): WidgetContainer.LayoutParams {
        return FrameLayoutWidget.LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return p is FrameLayoutWidget.LayoutParams
    }

    fun setContent(content: Widget?) {
        if (this.content === content) return

        clearChildren()

        if (content != null) addChild("content", content)
    }

    override fun addChild(name: String, child: Widget) {
        check(content == null) { "ScrollPanelWidget can host only one direct child. Use a container like LinearLayoutWidget as the single child." }
        child.name = name
        content = child
        super.addChild(name, child)
    }

    override fun removeChild(name: String) {
        if (content != null && content!!.name == name) clearChildren()
    }

    override fun clearChildren() {
        if (content != null) {
            super.removeChild(content!!.name)
            content = null
        }
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val lp = layoutParams
        var desiredWidth = lp.paddingLeft + lp.paddingRight
        var desiredHeight = lp.paddingTop + lp.paddingBottom

        if (content != null && content!!.isVisible()) {
            var contentWidthSpec = widthMeasureSpec
            var contentHeightSpec = heightMeasureSpec

            if (orientation == Orientation.VERTICAL) {
                val heightMode = heightMeasureSpec.mode
                if (heightMode == MeasureSpec.Mode.EXACTLY || heightMode == MeasureSpec.Mode.AT_MOST) {
                    contentHeightSpec = MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
                }
            } else {
                val widthMode = widthMeasureSpec.mode
                if (widthMode == MeasureSpec.Mode.EXACTLY || widthMode == MeasureSpec.Mode.AT_MOST) {
                    contentWidthSpec = MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
                }
            }

            measureChild(content!!, contentWidthSpec, contentHeightSpec)

            val contentLp = content!!.layoutParams
            desiredWidth += content!!.measuredWidth + contentLp.marginLeft + contentLp.marginRight
            desiredHeight += content!!.measuredHeight + contentLp.marginTop + contentLp.marginBottom
        }

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onLayout() {
        if (content != null && content!!.isVisible()) {
            content!!.layout(0f, 0f, content!!.measuredWidth, content!!.measuredHeight)

            val maxScrollX = max(0f, content!!.width - width)
            val maxScrollY = max(0f, content!!.height - height)

            var currentScrollX = scrollX
            var currentScrollY = scrollY

            var needsClamping = false
            if (currentScrollX > maxScrollX) {
                currentScrollX = maxScrollX
                needsClamping = true
            }
            if (currentScrollY > maxScrollY) {
                currentScrollY = maxScrollY
                needsClamping = true
            }

            if (needsClamping) scrollTo(currentScrollX, currentScrollY)

            scrollTargetX = Mth.clamp(scrollTargetX, 0f, maxScrollX)
            scrollTargetY = Mth.clamp(scrollTargetY, 0f, maxScrollY)
        }
        notifyScrollChanged()
    }

    override fun dispatchEvent(event: InputEvent) {
        if (!isAbsoluteEnabled() || !isVisible()) return

        if (event is MouseEvent && !isMouseOver(event.x, event.y)) {
            if (hoveredWidget != null) {
                hoveredWidget!!.isHovered = false
                hoveredWidget = null
            }

            if (gestureTarget != null) {
                gestureTarget!!.dispatchEvent(event)
                if (event.type == EventType.MOUSE_RELEASED) gestureTarget = null
            }
            return
        }

        super.dispatchEvent(event)
    }

    val maxScroll: Float
        get() = if (orientation == Orientation.VERTICAL) maxScrollY else maxScrollX

    private val maxScrollX: Float
        get() {
            val content = content ?: return 0f
            val lp = layoutParams
            val contentLp = content.layoutParams
            val contentWidth = content.measuredWidth + contentLp.marginLeft + contentLp.marginRight
            val viewWidth = width - lp.paddingLeft - lp.paddingRight
            return max(0f, contentWidth - viewWidth)
        }

    private val maxScrollY: Float
        get() {
            val content = content ?: return 0f
            val lp = layoutParams
            val contentLp = content.layoutParams
            val contentHeight = content.measuredHeight + contentLp.marginTop + contentLp.marginBottom
            val viewHeight = height - lp.paddingTop - lp.paddingBottom
            return max(0f, contentHeight - viewHeight)
        }

    fun scrollToEnd() {
        if (isLayoutDirty) {
            pendingScrollToEnd = true
            invalidate()
        } else {
            pendingScrollToEnd = false
            setScrollTarget(this.maxScroll)
        }
    }

    override fun render(context: Canvas) {
        if (!isVisible()) return

        if (pendingScrollToEnd) {
            pendingScrollToEnd = false
            if (orientation == Orientation.HORIZONTAL) scrollTargetX = maxScrollX
            else scrollTargetY = maxScrollY
        }

        scrollTo(
            Chase.approach(scrollX, scrollTargetX),
            Chase.approach(scrollY, scrollTargetY)
        )

        super.render(context)
    }

    override fun renderChildren(context: Canvas) {
        context.pose().pushPose()
        context.pose().translate(-scrollX, -scrollY)
        super.renderChildren(context)
        context.pose().popPose()
    }

    override fun onMouseScrolled(event: ScrollEvent) {
        if (isMouseOver(event.x, event.y)) {
            event.consume()
            scrollTargetY = Mth.clamp(scrollTargetY - (event.delta * scrollSpeed).toFloat(), 0f, maxScrollY)
            if (event.xDelta != 0.0) {
                scrollTargetX = Mth.clamp(scrollTargetX - (event.xDelta * scrollSpeed).toFloat(), 0f, maxScrollX)
            }
            invalidate()
        }
    }

    fun setScrollTarget(scrollTarget: Float): ScrollPanelWidget {
        if (orientation == Orientation.HORIZONTAL) {
            val clamped = Mth.clamp(scrollTarget, 0f, maxScrollX)
            if (this.scrollTargetX != clamped) {
                this.scrollTargetX = clamped
                invalidate()
            }
        } else {
            val clamped = Mth.clamp(scrollTarget, 0f, maxScrollY)
            if (this.scrollTargetY != clamped) {
                this.scrollTargetY = clamped
                invalidate()
            }
        }
        return this
    }

    fun setScrollSpeed(scrollSpeed: Float): ScrollPanelWidget {
        this.scrollSpeed = scrollSpeed
        return this
    }

    fun getPanelOrientation(): Orientation? = orientation

    fun getPanelScrollSpeed(): Float = scrollSpeed

    override fun scrollTo(x: Float, y: Float) {
        if (content == null) {
            val changed = scrollX != x || scrollY != y
            super.scrollTo(x, y)
            if (changed) notifyScrollChanged()
            return
        }

        val maxScrollX = max(0f, content!!.width - width)
        val maxScrollY = max(0f, content!!.height - height)

        val finalX = Mth.clamp(x, 0f, maxScrollX)
        val finalY = Mth.clamp(y, 0f, maxScrollY)

        val changed = scrollX != finalX || scrollY != finalY
        super.scrollTo(finalX, finalY)
        if (changed) notifyScrollChanged()
    }

    override fun scrollBy(dx: Float, dy: Float) {
        scrollTo(scrollX + dx, scrollY + dy)
    }
}
