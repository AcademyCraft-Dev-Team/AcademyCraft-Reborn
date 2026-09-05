package org.academy.api.client.gui.widget

import com.mojang.math.Axis
import net.minecraft.util.ARGB
import org.academy.AcademyCraft
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.BlurRegion
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.render.SubtreeCache
import org.academy.api.client.gui.util.GlyphCommandGenerator
import org.joml.Matrix4f
import java.util.*
import kotlin.math.max

abstract class AbstractWidgetContainer : AbstractWidget(), WidgetContainer {
    protected val protectedChildren: MutableMap<String, Widget> = LinkedHashMap()
    override val children: Map<String, Widget> get() = protectedChildren

    protected val dirtyChildrenSet: MutableSet<Widget> = linkedSetOf()
    override val dirtyChildren: Set<Widget> get() = dirtyChildrenSet

    /** 自身内容 (renderInternal) 的缓存, 对齐安卓 DisplayList: 只缓存本控件, 不展平子控件. */
    private var ownRenderCache: SubtreeCache? = null

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
            if (child == null) {
                field?.isFocused = false
                field = null
                return
            }
            val cP = child.parent
            if (cP !== this) {
                if (cP is WidgetContainer) cP.focusedChild = child
                return
            }

            if (field === child) {
                if (!child.isFocused) {
                    child.isFocused = true
                }
                return
            }

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
        context.pose().translate(0f, height - thickness)
        context.submit(FillRectDrawCommand(width, thickness, red, green, blue, alpha))
        context.pose().popPose()
        context.submit(FillRectDrawCommand(thickness, height, red, green, blue, alpha))
        context.pose().pushPose()
        context.pose().translate(width - thickness, 0f)
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
        context.pose().translate(padding, padding)

        context.submit(
            FillRectDrawCommand(
                textWidth + padding * 2,
                textHeight + padding * 2,
                backRed, backGreen, backBlue, backAlpha
            )
        )

        context.pose().pushPose()
        context.pose().translate(padding, padding)
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

    override fun invalidate() {
        // 定向失效 (对齐安卓): 仅重录自身内容并向上传播, 不递归后代.
        // 后代内容变化由各自 invalidate 自录; 位姿/alpha/scissor/drawOrder 变化均走缓存重组;
        // 布局尺寸变化由 layout() 按"尺寸变化"逐控件自录.
        isRenderDirty = true
        parent?.onChildInvalidated(this)
    }

    override fun onChildInvalidated(child: Widget) {
        dirtyChildrenSet.add(child)
        parent?.onChildInvalidated(this)
    }

    override fun render(context: RenderContext) {
        if (visibility != Widget.Visibility.VISIBLE) {
            return
        }

        val pivotX = width * originX
        val pivotY = height * originY

        val hasTransform = scaleX != 1.0f || scaleY != 1.0f || rotation != 0.0f

        context.pose().pushPose()
        context.drawOrder().push()
        run {
            context.drawOrder().advance()
            if (hasTransform) {
                context.pose().translate(pivotX, pivotY)
                if (rotation != 0.0f) {
                    context.pose().mulPose(Axis.ZP.rotationDegrees(rotation))
                }
                if (scaleX != 1.0f || scaleY != 1.0f) {
                    context.pose().scale(scaleX, scaleY)
                }
                context.pose().translate(-pivotX, -pivotY)
            }
            context.alpha().push(alpha)
            run {
                if (AcademyCraft.DEBUG_UI) {
                    renderDebugLayoutBounds(this, context)
                }
                renderOwnCached(context)
                renderChildren(context)
            }
            context.alpha().pop()
        }
        context.drawOrder().pop()
        context.pose().popPose()
    }

    private fun renderOwnCached(context: RenderContext) {
        if (AcademyCraft.DEBUG_UI || bypassRenderCache) {
            renderInternal(context)
            isRenderDirty = false
            return
        }
        val cache = ownRenderCache
        if (!isRenderDirty && cache != null) {
            if (context.addCached(cache, context.pose().last().pose())) return
        }
        val start = context.commands.size
        val blurStart = context.blurRegionCount()
        val origin = Matrix4f(context.pose().last().pose())
        renderInternal(context)
        val built = buildCache(context, start, blurStart, origin, 0L)
        // 祖先 alpha 过低 (如淡入首帧) 时烘焙颜色接近全透明, 缓存既无意义又会放大校正噪声, 不建立缓存.
        if (built != null && context.accumulatedAlpha > ALPHA_CACHE_EPSILON) {
            ownRenderCache = built
        } else {
            ownRenderCache = null
        }
        isRenderDirty = false
    }

    protected open fun renderChildren(context: RenderContext) {
        context.resetRecordedMax()
        for (child in children.values) {
            if (child.isVisible()) {
                context.drawOrder().push()
                if (child.coverAllPrev) context.drawOrder()
                    .advance(context.recordedMax + 1)
                context.pose().pushPose()
                run {
                    context.pose().translate(child.x, child.y)
                    context.pose().translate(child.translationX, child.translationY)
                    child.render(context)
                }
                context.pose().popPose()
                context.drawOrder().pop()
            }
        }

        if (AcademyCraft.DEBUG_UI) {
            for (child in children.values) {
                if (child.isVisible() && child !is WidgetContainer) {
                    context.pose().pushPose()
                    run {
                        context.pose().translate(child.x, child.y)
                        renderDebugLayoutBounds(child, context)
                    }
                    context.pose().popPose()
                }
            }
        }
    }

    /**
     * 把本次 [start, commands.size) 段录制为 [SubtreeCache] 喵.
     * 命令位姿按"世界位姿"保存 (与录制帧提交时一致); 祖先位姿未变时 fast path 直接复用,
     * 变化时以 `current * invRecordOrigin * worldPose` 重组. 位姿矩阵不可逆时返回 null (调用方不缓存) 喵.
     */
    private fun buildCache(
        context: RenderContext,
        start: Int,
        blurStart: Int,
        origin: Matrix4f,
        coverBaseRecordedMax: Long
    ): SubtreeCache? {
        // 录制期的祖先 scissor: 命中它的命令纯属祖先裁剪, 回放时用当前 scissor 栈重取 (对齐 P2-7),
        // 否则缓存会烘焙滚动面板的旧裁剪矩形导致深失效重录.
        val recordScissor = context.currentScissor()
        val baseDrawOrder = context.drawOrder().peek()
        val localized = ArrayList<SubmittedCommand>(context.commands.size - start)
        for (i in start until context.commands.size) {
            val c = context.commands[i]
            val localScissor = if (c.scissorRect == recordScissor) null else c.scissorRect
            // 保留世界位姿 (fast path 直接复用); drawOrder 存相对序 (减去本控件基准).
            localized.add(SubmittedCommand(c.command, c.pose, localScissor, c.drawOrder - baseDrawOrder, c.commandIndex))
        }
        return SubtreeCache(localized, context.blurRegionsSince(blurStart), origin, coverBaseRecordedMax, context.accumulatedAlpha, baseDrawOrder)
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
        if (needsOnLayout) {
            onLayout()
            // 自身尺寸变化已在 super.layout 中定向失效; 后代各自按自身尺寸变化失效,
            // 不再深失效 (位置变化走位姿重组, alpha/scissor/drawOrder 走缓存外置).
        }
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
            lp.widthMode,
            lp.widthPercent
        )
        val childHeightSpec = getChildMeasureSpec(
            parentHeightSpec,
            layoutParams.paddingTop + layoutParams.paddingBottom + lp.marginTop + lp.marginBottom,
            lp.height,
            lp.heightMode,
            lp.heightPercent
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
        invalidate()
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
            dirtyChildrenSet.remove(widget)
            requestLayout()
            invalidate()
        }
    }

    override fun replaceChild(name: String, child: Widget) {
        val old = protectedChildren[name]
        if (old != null) {
            if (old.isAttached()) {
                old.dispatchDetached()
            }
            old.parent = null
            if (focusedChild === old) focusedChild = null
            if (hoveredWidget === old) hoveredWidget = null
            if (gestureTarget === old) gestureTarget = null
            dirtyChildrenSet.remove(old)
        }

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
        invalidate()
    }

    override fun clearChildren() {
        children.keys.toList().forEach { removeChild(it) }
        requestLayout()
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

        /** 祖先 alpha 低于此值时不建立内容缓存 (淡入首帧), 避免校正乘子放大噪声. */
        private const val ALPHA_CACHE_EPSILON = 1e-3f

        fun getChildMeasureSpec(
            spec: MeasureSpec,
            padding: Float,
            childDimension: Float,
            childMode: SizeMode,
            childPercent: Float
        ): MeasureSpec {
            if (childMode == SizeMode.PERCENT) {
                val specSize = max(0f, spec.size - padding)
                val resultSize = specSize * childPercent.coerceIn(0f, 1f)
                val resultMode =
                    if (spec.mode == MeasureSpec.Mode.EXACTLY) MeasureSpec.Mode.EXACTLY
                    else MeasureSpec.Mode.AT_MOST
                return MeasureSpec(resultMode, resultSize)
            }
            return getChildMeasureSpec(spec, padding, childDimension, childMode)
        }

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

                        SizeMode.PERCENT -> {
                            resultSize = specSize * childDimension.coerceIn(0f, 1f)
                            resultMode = MeasureSpec.Mode.EXACTLY
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

                        SizeMode.PERCENT -> {
                            resultSize = specSize * childDimension.coerceIn(0f, 1f)
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

                        SizeMode.PERCENT -> {
                            resultSize = 0f
                        }
                    }
                }
            }
            return MeasureSpec(resultMode, resultSize)
        }
    }
}
