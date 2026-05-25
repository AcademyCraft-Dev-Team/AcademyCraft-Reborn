package org.academy.api.client.gui.widget

import com.mojang.math.Axis
import net.minecraft.util.ARGB
import org.academy.AcademyCraft
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.util.GlyphCommandGenerator
import java.util.*
import kotlin.math.max

abstract class AbstractWidgetContainer : AbstractWidget(), WidgetContainer {
    protected val protectedChildren: MutableMap<String, Widget> = LinkedHashMap()
    override val children: Map<String, Widget> get() = Collections.unmodifiableMap(protectedChildren)

    override var isLayoutDirty: Boolean = true
        protected set

    override var isFocused: Boolean
        get() = super.isFocused
        set(focused) {
            super.isFocused = focused
            val fC = focusedChild
            if (!focused && fC != null) {
                fC.isFocused = false
                focusedChild = null
            }
        }

    override var focusedChild: Widget? = null
        set(child) {
            if (child == null) return
            val cP = child.parent
            if (cP !== this) {
                if (cP is WidgetContainer) cP.focusedChild = child
                return
            }

            if (field === child) return

            field?.isFocused = false

            field = child

            if (field != null) {
                field!!.isFocused = true
                if (parent is WidgetContainer) parent!!.focusedChild = this
            }
        }
    override var hoveredWidget: Widget? = null
        protected set
    protected var gestureTarget: Widget? = null

    init {
        isClickable = true
    }

    private fun renderDebugLayoutBounds(widget: Widget, context: RenderContext) {
        var outlineColor = -0x10000
        if (widget.isFocused) outlineColor = -0xff0100
        else if (widget.isHovered) outlineColor = -0xffff01

        val red = ARGB.red(outlineColor) / 255.0f
        val green = ARGB.green(outlineColor) / 255.0f
        val blue = ARGB.blue(outlineColor) / 255.0f
        val alpha = 0.8f
        val thickness = 0.5f

        val width = widget.width
        val height = widget.height

        context.submit(FillRectDrawCommand(width, thickness, red, green, blue, alpha))
        context.pose().pushPose()
        context.pose().translate(0f, height - thickness, 0f)
        context.submit(FillRectDrawCommand(width, thickness, red, green, blue, alpha))
        context.pose().popPose()
        context.submit(FillRectDrawCommand(thickness, height, red, green, blue, alpha))
        context.pose().pushPose()
        context.pose().translate(width - thickness, 0f, 0f)
        context.submit(FillRectDrawCommand(thickness, height, red, green, blue, alpha))
        context.pose().popPose()

        if (widget.isHovered) renderDebugInfo(widget, context)
    }

    private fun renderDebugInfo(widget: Widget, context: RenderContext) {
        val namePart = if (widget.name.isEmpty()) "" else "'${widget.name}'"
        val infoText = "[${widget.javaClass.simpleName}] $namePart\n" +
                "Pos: (${"%.1f".format(widget.getAbsoluteX())}, ${"%.1f".format(widget.getAbsoluteY())}) " +
                "Size: (${"%.1f".format(widget.width)}, ${"%.1f".format(widget.height)}) " +
                "Alpha: ${"%.2f".format(widget.getAbsoluteAlpha())}"

        val fontSize = 6f
        val padding = 2f

        val textWidth = LabelWidget.getTextWidth(infoText, fontSize)
        val textHeight = LabelWidget.getTextHeight(infoText, fontSize)

        val textRed = 1f
        val textGreen = 1f
        val textBlue = 1f
        val textAlpha = 0.82f
        val backRed = 0f
        val backGreen = 0f
        val backBlue = 0f
        val backAlpha = 0.56f

        context.pose().pushPose()
        context.pose().translate(padding, padding, 500f)

        context.submit(
            FillRectDrawCommand(
                textWidth + padding * 2,
                textHeight + padding * 2,
                backRed, backGreen, backBlue, backAlpha
            )
        )

        context.pose().pushPose()
        context.pose().translate(padding, padding, 0.1f)
        val commands = GlyphCommandGenerator.generate(
            infoText, fontSize, 0f, textRed, textGreen, textBlue, textAlpha
        )
        for (command in commands) {
            context.submit(command)
        }
        context.pose().popPose()

        context.pose().popPose()
    }

    private fun findTopWidgetAt(mouseX: Double, mouseY: Double): Widget? {
        val childrenList = ArrayList(children.values)
        childrenList.reverse()

        for (child in childrenList) {
            if (!child.isVisible() || !child.isAbsoluteEnabled()) {
                continue
            }
            if (child.isMouseOver(mouseX, mouseY)) {
                if (child is AbstractWidgetContainer) {
                    val nestedChild = child.findTopWidgetAt(mouseX, mouseY)
                    return nestedChild ?: child
                } else {
                    return child
                }
            }
        }

        if (isMouseOver(mouseX, mouseY)) {
            return this
        }

        return null
    }

    override fun requestLayout() {
        isLayoutDirty = true
        super.requestLayout()
    }

    override fun render(context: RenderContext) {
        if (visibility != Widget.Visibility.VISIBLE) {
            return
        }

        val pivotX = width * originX
        val pivotY = height * originY

        val hasTransform = scaleX != 1.0f || scaleY != 1.0f || rotation != 0.0f

        context.pose().pushPose()
        run {
            if (hasTransform) {
                context.pose().translate(pivotX, pivotY, 0f)
                if (rotation != 0.0f) {
                    context.pose().mulPose(Axis.ZP.rotationDegrees(rotation))
                }
                if (scaleX != 1.0f || scaleY != 1.0f) {
                    context.pose().scale(scaleX, scaleY, 1.0f)
                }
                context.pose().translate(-pivotX, -pivotY, 0f)
            }
            context.alpha().push(alpha)
            run {
                if (AcademyCraft.DEBUG_UI) {
                    renderDebugLayoutBounds(this, context)
                }
                renderInternal(context)
                renderChildren(context)
            }
            context.alpha().pop()
        }
        context.pose().popPose()
    }

    protected open fun renderChildren(context: RenderContext) {
        for (child in children.values) {
            if (child.isVisible()) {
                context.pose().pushPose()
                run {
                    context.pose().translate(child.x, child.y, child.z)
                    context.pose().translate(child.translationX, child.translationY, 0f)
                    child.render(context)
                }
                context.pose().popPose()
            }
        }

        if (AcademyCraft.DEBUG_UI) {
            for (child in children.values) {
                if (child.isVisible() && child !is WidgetContainer) {
                    context.pose().pushPose()
                    run {
                        context.pose().translate(child.x, child.y, child.z + 0.1f)
                        renderDebugLayoutBounds(child, context)
                    }
                    context.pose().popPose()
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        var maxWidth = 0.0f
        var maxHeight = 0.0f

        for (child in children.values) {
            if (!child.isVisible()) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val lp = child.layoutParams
            maxWidth = max(maxWidth, child.measuredWidth + lp.marginLeft + lp.marginRight)
            maxHeight = max(maxHeight, child.measuredHeight + lp.marginTop + lp.marginBottom)
        }

        val lp = layoutParams
        maxWidth += lp.paddingLeft + lp.paddingRight
        maxHeight += lp.paddingTop + lp.paddingBottom

        setMeasuredDimension(
            resolveSize(maxWidth, widthMeasureSpec),
            resolveSize(maxHeight, heightMeasureSpec)
        )
    }

    override fun layout(left: Float, top: Float, right: Float, bottom: Float) {
        super.layout(left, top, right, bottom)
        onLayout()
        isLayoutDirty = false
    }

    protected open fun onLayout() {
        val lp = layoutParams
        val parentLeft = lp.paddingLeft
        val parentTop = lp.paddingTop
        val parentRight = width - lp.paddingRight
        val parentBottom = height - lp.paddingBottom
        val availableWidth = parentRight - parentLeft
        val availableHeight = parentBottom - parentTop

        for (child in children.values) {
            if (!child.isVisible()) continue

            val childLp = child.layoutParams
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            var childLeft = parentLeft + childLp.marginLeft
            var childTop = parentTop + childLp.marginTop

            val verticalGravity = childLp.gravity shr Gravity.AXIS_Y_SHIFT and 0x7
            val horizontalGravity = childLp.gravity shr Gravity.AXIS_X_SHIFT and 0x7

            if (horizontalGravity == Gravity.AXIS_SPECIFIED) {
                childLeft += (availableWidth - childWidth - childLp.marginLeft - childLp.marginRight) / 2.0f
            } else if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) {
                childLeft = parentRight - childWidth - childLp.marginRight
            }

            if (verticalGravity == Gravity.AXIS_SPECIFIED) {
                childTop += (availableHeight - childHeight - childLp.marginTop - childLp.marginBottom) / 2.0f
            } else if ((verticalGravity and Gravity.AXIS_PULL_AFTER) != 0) {
                childTop = parentBottom - childHeight - childLp.marginBottom
            }

            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
        }
    }

    protected fun measureChild(child: Widget, parentWidthSpec: MeasureSpec, parentHeightSpec: MeasureSpec) {
        val lp = child.layoutParams
        val childWidthSpec = getChildMeasureSpec(
            parentWidthSpec,
            layoutParams.paddingLeft + layoutParams.paddingRight + lp.marginLeft + lp.marginRight,
            lp.width,
            lp.widthMode
        )
        val childHeightSpec = getChildMeasureSpec(
            parentHeightSpec,
            layoutParams.paddingTop + layoutParams.paddingBottom + lp.marginTop + lp.marginBottom,
            lp.height,
            lp.heightMode
        )
        child.measure(childWidthSpec, childHeightSpec)
    }

    override fun dispatchEvent(event: InputEvent) {
        if (!isAbsoluteEnabled() || visibility != Widget.Visibility.VISIBLE) return

        val intercepted = onInterceptEvent(event)
        if (!intercepted) {
            if (event.type == EventType.MOUSE_MOVED) {
                val newHoveredWidget = findTopWidgetAt((event as MouseEvent).x, event.y)
                if (hoveredWidget !== newHoveredWidget) {
                    var current = hoveredWidget
                    while (current != null) {
                        if (newHoveredWidget != null && isAncestor(current, newHoveredWidget)) break
                        current.isHovered = false
                        current = current.parent
                    }

                    hoveredWidget = newHoveredWidget

                    current = hoveredWidget
                    while (current != null) {
                        current.isHovered = true
                        current = current.parent
                    }
                }
            }

            if (gestureTarget != null) {
                gestureTarget!!.dispatchEvent(event)
                if (event.type == EventType.MOUSE_RELEASED) {
                    if (AcademyCraft.DEBUG_UI) LOGGER.debug("[UI Event] gestureTarget released.")
                    gestureTarget = null
                }
                return
            }

            val childrenList = ArrayList(children.values)
            childrenList.reverse()

            for (child in childrenList) {
                if (!child.isVisible() || !child.isAbsoluteEnabled()) continue

                child.dispatchEvent(event)

                if (event.isConsumed) {
                    if (AcademyCraft.DEBUG_UI) {
                        LOGGER.debug(
                            "[UI Event] Event consumed by child '{}'. Stopping propagation in '{}'.",
                            child.name,
                            name
                        )
                    }
                    if (event.type == EventType.MOUSE_PRESSED) {
                        gestureTarget = child
                        focusedChild = if (child.canFocus()) child else this
                    }
                    return
                }
            }
        } else {
            if (hoveredWidget != null) {
                var current = hoveredWidget
                while (current != null && current !== this) {
                    current.isHovered = false
                    current = current.parent
                }
                hoveredWidget = null
            }
        }

        super.dispatchEvent(event)
        if (event.isConsumed && event.type == EventType.MOUSE_PRESSED) {
            focusedChild = this
        }
    }

    private fun isAncestor(ancestor: Widget?, descendant: Widget?): Boolean {
        if (ancestor == null || descendant == null) return false
        var current: Widget? = descendant
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    override fun addChild(name: String, child: Widget) {
        val cp = child.parent
        cp?.removeChild(name)

        var lp = child.layoutParams
        if (lp === WidgetContainer.LayoutParams.NONE) {
            lp = generateDefaultLayoutParams()
        }
        if (!checkLayoutParams(lp)) {
            lp = generateLayoutParams(lp)
        }
        child.layoutParams = lp

        child.parent = this
        child.name = name
        protectedChildren[name] = child

        if (isAttached()) {
            child.dispatchAttached()
        }

        requestLayout()
    }

    override fun removeChild(name: String) {
        val widget = children[name]
        if (widget != null) {
            if (widget.isAttached()) {
                widget.dispatchDetached()
            }

            widget.parent = null
            if (focusedChild === widget) {
                focusedChild = null
            }
            if (hoveredWidget === widget) {
                hoveredWidget = null
            }
            if (gestureTarget === widget) {
                gestureTarget = null
            }
            protectedChildren.remove(name)
            requestLayout()
        }
    }

    override fun clearChildren() {
        for (child in children.values) removeChild(child.name)
        requestLayout()
    }

    override fun tick() {
        for (tickable in children.values) tickable.tick()
    }

    override fun canFocus(): Boolean {
        return true
    }

    override fun dispatchAttached() {
        super.dispatchAttached()
        for (child in children.values) {
            child.dispatchAttached()
        }
    }

    override fun dispatchDetached() {
        for (child in children.values) {
            child.dispatchDetached()
        }
        super.dispatchDetached()
    }

    companion object {
        private val LOGGER = AcademyCraft.getLogger()

        fun getChildMeasureSpec(
            spec: MeasureSpec,
            padding: Float,
            childDimension: Float,
            childMode: SizeMode
        ): MeasureSpec {
            val specSize = max(0f, spec.size - padding)

            var resultSize: Float
            var resultMode = MeasureSpec.Mode.UNSPECIFIED

            when (spec.mode) {
                MeasureSpec.Mode.EXACTLY -> {
                    when (childMode) {
                        SizeMode.FIXED -> {
                            resultSize = childDimension
                            resultMode = MeasureSpec.Mode.EXACTLY
                        }

                        SizeMode.MATCH_PARENT -> {
                            resultSize = specSize
                            resultMode = MeasureSpec.Mode.EXACTLY
                        }

                        SizeMode.WRAP_CONTENT -> {
                            resultSize = specSize
                            resultMode = MeasureSpec.Mode.AT_MOST
                        }
                    }
                }

                MeasureSpec.Mode.AT_MOST -> {
                    when (childMode) {
                        SizeMode.FIXED -> {
                            resultSize = childDimension
                            resultMode = MeasureSpec.Mode.EXACTLY
                        }

                        SizeMode.MATCH_PARENT -> {
                            resultSize = specSize
                            resultMode = MeasureSpec.Mode.AT_MOST
                        }

                        SizeMode.WRAP_CONTENT -> {
                            resultSize = specSize
                            resultMode = MeasureSpec.Mode.AT_MOST
                        }
                    }
                }

                MeasureSpec.Mode.UNSPECIFIED -> {
                    when (childMode) {
                        SizeMode.FIXED -> {
                            resultSize = childDimension
                            resultMode = MeasureSpec.Mode.EXACTLY
                        }

                        SizeMode.MATCH_PARENT -> {
                            resultSize = 0f
                        }

                        SizeMode.WRAP_CONTENT -> {
                            resultSize = 0f
                        }
                    }
                }
            }
            return MeasureSpec(resultMode, resultSize)
        }
    }
}