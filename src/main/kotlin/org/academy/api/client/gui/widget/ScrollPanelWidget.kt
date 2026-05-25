package org.academy.api.client.gui.widget

import net.minecraft.util.Mth
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.render.ScissorRect
import org.academy.api.client.util.ClientUtil
import kotlin.math.max

open class ScrollPanelWidget(protected val orientation: Orientation? = Orientation.VERTICAL) :
    AbstractWidgetContainer() {
    protected var scrollTarget: Float = 0f
    protected var scrollSpeed: Float = 24f

    private var content: Widget? = null

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
        }
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

        val transformedEvent = transformEvent(event)

        if (content != null && content!!.isVisible() && content!!.isAbsoluteEnabled()) {
            content!!.dispatchEvent(transformedEvent)
            if (transformedEvent.isConsumed) {
                event.consume()
                if (transformedEvent.type == EventType.MOUSE_PRESSED) {
                    gestureTarget = content
                    focusedChild = if (content!!.canFocus()) content else this
                }
                return
            }
        }

        super.dispatchEvent(event)
        if (event.isConsumed && event.type == EventType.MOUSE_PRESSED) focusedChild = this
    }

    private fun transformEvent(event: InputEvent): InputEvent {
        if (event is MouseEvent) {
            val transformedX = event.x - getAbsoluteX() + scrollX
            val transformedY = event.y - getAbsoluteY() + scrollY

            return when (event.type) {
                EventType.MOUSE_PRESSED -> MouseEvent.createPressEvent(transformedX, transformedY, event.button)
                EventType.MOUSE_RELEASED -> MouseEvent.createReleaseEvent(transformedX, transformedY, event.button)
                EventType.MOUSE_MOVED -> MouseEvent.createMoveEvent(transformedX, transformedY)
                EventType.MOUSE_DRAGGED -> MouseEvent.createDragEvent(
                    transformedX,
                    transformedY,
                    event.button,
                    event.dragX,
                    event.dragY
                )

                else -> event
            }
        }

        if (event is ScrollEvent) {
            val transformedX = event.x - getAbsoluteX() + scrollX
            val transformedY = event.y - getAbsoluteY() + scrollY
            return ScrollEvent(transformedX, transformedY, event.delta)
        }

        return event
    }

    val maxScroll: Float
        get() {
            if (content == null) return 0f

            val lp = layoutParams
            val contentLp = content!!.layoutParams

            if (orientation == Orientation.VERTICAL) {
                val contentHeight = content!!.measuredHeight + contentLp.marginTop + contentLp.marginBottom
                val viewHeight = height - lp.paddingTop - lp.paddingBottom
                return max(0f, contentHeight - viewHeight)
            } else {
                val contentWidth = content!!.measuredWidth + contentLp.marginLeft + contentLp.marginRight
                val viewWidth = width - lp.paddingLeft - lp.paddingRight
                return max(0f, contentWidth - viewWidth)
            }
        }

    fun scrollToEnd() {
        requestLayout()
        setScrollTarget(this.maxScroll)
    }

    override fun render(context: RenderContext) {
        if (!isVisible()) return

        val currentScrollY = scrollY
        val newScrollY = Mth.lerp(ClientUtil.animationFactor(Mth.PI / 1.5f), currentScrollY, scrollTarget)
        scrollTo(scrollX, newScrollY)

        val alpha1 = alpha
        context.alpha().push(alpha1)
        val scissor = ScissorRect(
            getAbsoluteX() + getAbsoluteTranslationX(), getAbsoluteY() + getAbsoluteTranslationY(),
            width, height
        )
        context.enableScissor(scissor)
        run {
            context.pose().pushPose()
            run {
                context.pose().translate(-scrollX, -scrollY, 0f)
                if (content != null && content!!.isVisible()) {
                    renderChildren(context)
                }
            }
            context.pose().popPose()
        }
        context.disableScissor()
        context.alpha().pop()
    }

    override fun onMouseScrolled(event: ScrollEvent) {
        if (isMouseOver(event.x, event.y)) {
            event.consume()
            scrollTarget -= (event.delta * scrollSpeed).toFloat()
            val max = this.maxScroll
            scrollTarget = Mth.clamp(scrollTarget, 0f, max)
        }
    }

    fun setScrollTarget(scrollTarget: Float): ScrollPanelWidget {
        val max = this.maxScroll
        this.scrollTarget = Mth.clamp(scrollTarget, 0f, max)
        return this
    }

    fun setScrollSpeed(scrollSpeed: Float): ScrollPanelWidget {
        this.scrollSpeed = scrollSpeed
        return this
    }

    override fun scrollTo(x: Float, y: Float) {
        if (content == null) {
            super.scrollTo(x, y)
            return
        }

        val maxScrollX = max(0f, content!!.width - width)
        val maxScrollY = max(0f, content!!.height - height)

        val finalX = Math.clamp(x, 0f, maxScrollX)
        val finalY = Math.clamp(y, 0f, maxScrollY)

        super.scrollTo(finalX, finalY)
    }

    override fun scrollBy(dx: Float, dy: Float) {
        scrollTo(scrollX + dx, scrollY + dy)
    }
}