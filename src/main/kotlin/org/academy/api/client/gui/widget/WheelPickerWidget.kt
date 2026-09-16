package org.academy.api.client.gui.widget

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.event.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.util.Chase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

open class WheelPickerWidget : AbstractWidgetContainer() {
    enum class ItemAlign {
        CENTER, LEFT, RIGHT
    }

    interface OnItemSelectedListener {
        fun onItemSelected(picker: WheelPickerWidget, item: Widget?, position: Int)
    }

    interface OnWheelScrollListener {
        fun onWheelScrolled(offset: Float)

        fun onScrollStateChanged(state: Int)
    }

    var visibleItemCount: Int = DEFAULT_VISIBLE_ITEM_COUNT
        set(value) {
            val clamped = max(1, value)
            if (field != clamped) {
                field = clamped
                requestLayout()
            }
        }

    var itemSpace: Float = DEFAULT_ITEM_SPACE
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var isCyclic: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isCurtain: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var curtainColor: Int = 0x33FFFFFF

    var isIndicator: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var indicatorColor: Int = -0x1

    var indicatorSize: Float = 2.0f

    var itemAlign: ItemAlign = ItemAlign.CENTER
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isAtmospheric: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isSelectedScaleEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    val selectedPosition: Int
        get() = _selectedPosition

    val targetSelectedPosition: Int
        get() = if (itemCount == 0) {
            0
        } else if (isCyclic) {
            normalizePosition(_selectedPosition + targetPosition)
        } else {
            (_selectedPosition + targetPosition).coerceIn(0, itemCount - 1)
        }

    val currentPosition: Int
        get() = _currentPosition

    var scrollOffset: Float = 0f
        private set

    var onItemSelectedListener: OnItemSelectedListener? = null
    var onWheelScrollListener: OnWheelScrollListener? = null

    private var _selectedPosition = 0
    private var _currentPosition = 0

    private var targetPosition = 0

    private var isFlinging = false

    private var computedItemHeight: Float = 0f
    private var forcedItemHeight: Float = 0f

    private var scrollState = SCROLL_STATE_IDLE

    private var isDragging = false
    private var dragMoved = false
    private var lastDragY = 0.0
    private var lastDragTime = 0L

    private var velocityY = 0.0

    private val scrollTarget: Float
        get() = -targetPosition * itemHeight

    val itemHeight: Float
        get() = if (forcedItemHeight > 0f) forcedItemHeight else computedItemHeight

    protected val itemCount: Int
        get() = children.size

    protected val items: List<Widget>
        get() = children.values.toList()

    init {
        isClickable = true
    }

    override fun generateDefaultLayoutParams(): WidgetContainer.LayoutParams {
        return WidgetContainer.LayoutParams()
    }

    override fun generateLayoutParams(p: WidgetContainer.LayoutParams): WidgetContainer.LayoutParams {
        return WidgetContainer.LayoutParams(p)
    }

    override fun checkLayoutParams(p: WidgetContainer.LayoutParams): Boolean {
        return true
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val childWidthSpec = MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)
        val childHeightSpec = MeasureSpec(MeasureSpec.Mode.UNSPECIFIED, 0f)

        var maxChildWidth = 0.0f
        var maxChildHeight = 0.0f
        for (child in children.values) {
            if (!child.isVisible()) continue
            child.measure(childWidthSpec, childHeightSpec)
            maxChildWidth = max(maxChildWidth, child.measuredWidth)
            maxChildHeight = max(maxChildHeight, child.measuredHeight)
        }

        computedItemHeight = if (forcedItemHeight > 0f) forcedItemHeight else maxChildHeight + itemSpace

        val lp = layoutParams
        val desiredWidth = maxChildWidth + lp.paddingLeft + lp.paddingRight
        val desiredHeight = computedItemHeight * visibleItemCount + lp.paddingTop + lp.paddingBottom

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
        clampSelection()
    }

    override fun onLayout() {
        for (child in children.values) {
            if (!child.isVisible()) continue
            child.layout(0f, 0f, child.measuredWidth, child.measuredHeight)
        }
        clampSelection()
    }

    override fun addChild(name: String, child: Widget) {
        super.addChild(name, child)
        clampSelection()
    }

    override fun removeChild(name: String) {
        super.removeChild(name)
        clampSelection()
    }

    override fun clearChildren() {
        super.clearChildren()
        applyPosition(0)
    }

    override fun render(context: Canvas) {
        if (visibility != Widget.Visibility.VISIBLE) return

        chaseScrollTarget()

        context.pose().pushPose()
        run {
            applyTransform(context)
            context.alpha().push(alpha)
            run {
                renderInternal(context)
                renderWheel(context)
            }
            context.alpha().pop()
        }
        context.pose().popPose()
        isRenderDirty = false
        dirtyChildrenSet.clear()
    }

    private fun chaseScrollTarget() {
        if (isDragging || itemCount == 0 || itemHeight <= 0f) return
        if (targetPosition == 0 && scrollOffset == 0f) return
        val newOffset = Chase.approach(scrollOffset, scrollTarget)
        if (abs(scrollTarget - newOffset) < SETTLE_EPSILON_PX) {
            scrollOffset = scrollTarget
            settleToTarget()
        } else {
            scrollOffset = newOffset
            _currentPosition = _selectedPosition - round(scrollOffset / itemHeight).toInt()
            onWheelScrollListener?.onWheelScrolled(scrollOffset)
            invalidate()
        }
    }

    private fun renderWheel(context: Canvas) {
        val lp = layoutParams
        val contentTop = lp.paddingTop
        val contentBottom = height - lp.paddingBottom
        if (contentBottom <= contentTop) return
        val centerY = (contentTop + contentBottom) / 2f

        context.clipRect(0f, 0f, width, height)
        run {
            renderItems(context, centerY, contentTop, contentBottom)
            if (isCurtain) renderCurtain(context, centerY)
            if (isIndicator) renderIndicator(context, centerY)
        }
        context.disableScissor()
    }

    private fun renderItems(context: Canvas, centerY: Float, contentTop: Float, contentBottom: Float) {
        if (itemCount == 0 || itemHeight <= 0f) return

        val start = Mth.floor(_selectedPosition + (contentTop - centerY - scrollOffset) / itemHeight) - 1
        val end = Mth.ceil(_selectedPosition + (contentBottom - centerY - scrollOffset) / itemHeight) + 1

        val list = items
        for (i in start..end) {
            val contentIndex = if (isCyclic) normalizePosition(i) else i
            if (contentIndex < 0 || contentIndex >= list.size) continue
            val itemCenterY = centerY + (i - _selectedPosition) * itemHeight + scrollOffset
            val distanceRatio = abs(itemCenterY - centerY) / itemHeight
            renderItem(context, list[contentIndex], itemCenterY, distanceRatio)
        }
    }

    protected open fun renderItem(context: Canvas, child: Widget, centerY: Float, distanceRatio: Float) {
        val scale = computeItemScale(distanceRatio)
        val alpha = computeItemAlpha(distanceRatio)
        val childWidth = child.width
        val childHeight = child.height

        val alignX = computeItemAlignX(child, childWidth)
        val pivotX = computeItemPivotX()

        context.pose().pushPose()
        run {
            context.pose().translate(alignX, centerY - childHeight / 2f)
            if (scale != 1f) {
                context.pose().translate(pivotX, childHeight / 2f)
                context.pose().scale(scale, scale)
                context.pose().translate(-pivotX, -childHeight / 2f)
            }
            context.alpha().push(alpha)
            run {
                child.bypassRenderCache = true
                try {
                    child.render(context)
                } finally {
                    child.bypassRenderCache = false
                }
            }
            context.alpha().pop()
        }
        context.pose().popPose()
    }

    protected open fun resolveItemAlign(child: Widget): ItemAlign {
        val horizontalGravity = child.layoutParams.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        if (horizontalGravity == Gravity.CENTER_HORIZONTAL) return ItemAlign.CENTER
        if ((horizontalGravity and Gravity.AXIS_PULL_AFTER) != 0) return ItemAlign.RIGHT
        if ((horizontalGravity and Gravity.AXIS_PULL_BEFORE) != 0) return ItemAlign.LEFT
        return itemAlign
    }

    protected open fun computeItemAlignX(child: Widget, childWidth: Float): Float {
        return when (resolveItemAlign(child)) {
            ItemAlign.LEFT -> 0f
            ItemAlign.RIGHT -> width - childWidth
            ItemAlign.CENTER -> (width - childWidth) / 2f
        }
    }

    protected open fun computeItemPivotX(): Float {
        return width / 2f
    }

    protected open fun computeItemAlpha(distanceRatio: Float): Float {
        if (!isAtmospheric) return 1f
        return Mth.clamp(1.0f - distanceRatio * 0.4f, 0.15f, 1.0f)
    }

    protected open fun computeItemScale(distanceRatio: Float): Float {
        if (!isSelectedScaleEnabled) return 1f
        return 1f - Mth.clamp(distanceRatio * 0.08f, 0.0f, 0.08f)
    }

    protected open fun renderCurtain(context: Canvas, centerY: Float) {
        val alpha = ARGB.alpha(curtainColor) / 255.0f * context.accumulatedAlpha
        if (alpha <= 0f) return
        val halfItem = itemHeight / 2f
        context.pose().pushPose()
        run {
            context.pose().translate(0f, centerY - halfItem)
            context.submit(
                FillRectDrawCommand(
                    width,
                    itemHeight,
                    ARGB.red(curtainColor) / 255.0f,
                    ARGB.green(curtainColor) / 255.0f,
                    ARGB.blue(curtainColor) / 255.0f,
                    alpha
                )
            )
        }
        context.pose().popPose()
    }

    protected open fun renderIndicator(context: Canvas, centerY: Float) {
        val alpha = ARGB.alpha(indicatorColor) / 255.0f * context.accumulatedAlpha
        if (alpha <= 0f) return
        val halfItem = itemHeight / 2f
        val halfSize = indicatorSize / 2f
        val red = ARGB.red(indicatorColor) / 255.0f
        val green = ARGB.green(indicatorColor) / 255.0f
        val blue = ARGB.blue(indicatorColor) / 255.0f

        context.pose().pushPose()
        run {
            context.pose().translate(0f, centerY - halfItem - halfSize)
            context.submit(FillRectDrawCommand(width, indicatorSize, red, green, blue, alpha))
        }
        context.pose().popPose()
        context.pose().pushPose()
        run {
            context.pose().translate(0f, centerY + halfItem - halfSize)
            context.submit(FillRectDrawCommand(width, indicatorSize, red, green, blue, alpha))
        }
        context.pose().popPose()
    }

    override fun onInterceptEvent(event: InputEvent): Boolean {
        return when (event.type) {
            EventType.MOUSE_PRESSED, EventType.MOUSE_DRAGGED, EventType.MOUSE_RELEASED -> true
            else -> false
        }
    }

    override fun onMousePressed(event: MouseEvent) {
        if (event.button == 0 && isMouseOver(event.x, event.y)) {
            targetPosition = 0
            isFlinging = false
            isDragging = true
            dragMoved = false
            lastDragY = event.y
            lastDragTime = System.currentTimeMillis()
            velocityY = 0.0
            setScrollState(SCROLL_STATE_DRAGGING)
            event.consume()
            invalidate()
        }
    }

    override fun onMouseDragged(event: MouseEvent) {
        if (!isDragging || event.button != 0) return
        val dy = event.y - lastDragY
        val now = System.currentTimeMillis()
        val dtMs = now - lastDragTime
        lastDragTime = now
        if (dtMs > 0) {
            val dtSec = (dtMs / 1000.0).coerceAtLeast(MIN_VELOCITY_DT_SEC)
            val instantVelocity = dy / dtSec
            if (instantVelocity.isFinite()) {
                velocityY = if (velocityY == 0.0) {
                    instantVelocity.coerceIn(-MAX_FLING_VELOCITY.toDouble(), MAX_FLING_VELOCITY.toDouble())
                } else {
                    (velocityY * VELOCITY_SMOOTHING + instantVelocity * (1.0 - VELOCITY_SMOOTHING))
                        .coerceIn(-MAX_FLING_VELOCITY.toDouble(), MAX_FLING_VELOCITY.toDouble())
                }
            }
        }
        if (dy != 0.0) {
            scrollOffset += dy.toFloat()
            lastDragY = event.y
            dragMoved = true
            clampDragOffset()
            onWheelScrollListener?.onWheelScrolled(scrollOffset)
            invalidate()
        }
        event.consume()
    }

    override fun onMouseReleased(event: MouseEvent) {
        if (!isDragging || event.button != 0) return
        isDragging = false
        if (!dragMoved) {
            handleClick(event)
        } else {
            startSnapAnimation()
        }
        event.consume()
    }

    override fun onMouseScrolled(event: ScrollEvent) {
        if (isMouseOver(event.x, event.y)) {
            event.consume()
            scrollByItems(if (event.delta > 0) -1 else 1)
        }
    }

    override fun onKeyPressed(event: KeyEvent) {
        if (!isFocused) return
        when (event.keyCode) {
            InputConstants.KEY_UP -> {
                event.consume()
                scrollByItems(-1)
            }

            InputConstants.KEY_DOWN -> {
                event.consume()
                scrollByItems(1)
            }
        }
    }

    override fun canFocus(): Boolean {
        return true
    }

    fun getSelectedItem(): Widget? {
        if (itemCount == 0) return null
        return items[if (isCyclic) normalizePosition(_selectedPosition) else _selectedPosition]
    }

    open fun setSelectedPosition(position: Int): WheelPickerWidget {
        if (itemCount == 0) return this
        animateToPosition(position)
        return this
    }

    open fun animateToPosition(position: Int) {
        if (itemCount == 0) return
        if (isCyclic) {
            val n = itemCount
            val currentVisual = _selectedPosition + targetPosition
            val diff = ((normalizePosition(position) - currentVisual) % n + n) % n
            val step = if (diff > n / 2) diff - n else diff
            if (step == 0) return
            targetPosition += step
        } else {
            val target = position.coerceIn(0, itemCount - 1)
            if (target == _selectedPosition + targetPosition) return
            targetPosition = target - _selectedPosition
        }
        startChase()
    }

    open fun scrollByItems(direction: Int) {
        if (itemCount == 0) return
        if (isCyclic) {
            val n = itemCount
            val d = ((direction % n) + n) % n
            val step = if (d > n / 2) d - n else d
            targetPosition += step
            startChase()
        } else {
            val target = (_selectedPosition + targetPosition + direction).coerceIn(0, itemCount - 1)
            if (target != _selectedPosition + targetPosition) {
                targetPosition = target - _selectedPosition
                startChase()
            }
        }
    }

    private fun startChase() {
        setScrollState(SCROLL_STATE_SCROLLING)
        invalidate()
    }

    fun setItemHeight(height: Float): WheelPickerWidget {
        val clamped = max(0f, height)
        if (forcedItemHeight != clamped) {
            forcedItemHeight = clamped
            requestLayout()
        }
        return this
    }

    fun setVisibleItemCount(count: Int): WheelPickerWidget {
        visibleItemCount = count
        return this
    }

    fun setItemSpace(space: Float): WheelPickerWidget {
        itemSpace = space
        return this
    }

    fun setCyclic(cyclic: Boolean): WheelPickerWidget {
        isCyclic = cyclic
        return this
    }

    fun setCurtain(curtain: Boolean): WheelPickerWidget {
        isCurtain = curtain
        return this
    }

    fun setCurtainColor(color: Int): WheelPickerWidget {
        curtainColor = color
        return this
    }

    fun setIndicator(indicator: Boolean): WheelPickerWidget {
        isIndicator = indicator
        return this
    }

    fun setIndicatorColor(color: Int): WheelPickerWidget {
        indicatorColor = color
        return this
    }

    fun setIndicatorSize(size: Float): WheelPickerWidget {
        indicatorSize = size
        return this
    }

    fun setItemAlign(align: ItemAlign): WheelPickerWidget {
        itemAlign = align
        return this
    }

    fun setAtmospheric(atmospheric: Boolean): WheelPickerWidget {
        isAtmospheric = atmospheric
        return this
    }

    fun setSelectedScaleEnabled(enabled: Boolean): WheelPickerWidget {
        isSelectedScaleEnabled = enabled
        return this
    }

    fun setOnItemSelectedListener(listener: OnItemSelectedListener?): WheelPickerWidget {
        onItemSelectedListener = listener
        return this
    }

    fun setOnItemSelectedListener(listener: (picker: WheelPickerWidget, item: Widget?, position: Int) -> Unit): WheelPickerWidget {
        return setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun onItemSelected(picker: WheelPickerWidget, item: Widget?, position: Int) {
                listener(picker, item, position)
            }
        })
    }

    fun setOnWheelScrollListener(listener: OnWheelScrollListener?): WheelPickerWidget {
        onWheelScrollListener = listener
        return this
    }

    private fun computeSnapTarget(): Pair<Int, Float> {
        val n = itemCount
        if (n == 0 || itemHeight <= 0f) return _selectedPosition to 0f
        var delta = round(scrollOffset / itemHeight).toInt()
        if (isCyclic) {
            delta = ((delta % n) + n) % n
            if (delta > n / 2) delta -= n
        } else {
            delta = _selectedPosition - (_selectedPosition - delta).coerceIn(0, n - 1)
        }
        return (_selectedPosition - delta) to (delta * itemHeight)
    }

    private fun startSnapAnimation() {
        if (itemCount == 0) return
        var targetPos = computeSnapTarget().first
        isFlinging = false
        if (abs(velocityY) > FLING_VELOCITY_THRESHOLD) {
            val v = velocityY.toFloat()
            var extra = round(v / itemHeight * FLING_MOMENTUM_FACTOR).toInt()
            if (extra == 0) extra = if (v > 0) 1 else -1
            targetPos = if (isCyclic) {
                targetPos + extra
            } else {
                (targetPos + extra).coerceIn(0, itemCount - 1)
            }
            isFlinging = true
        }
        targetPosition = targetPos - _selectedPosition
        startChase()
    }

    private fun settleToTarget() {
        _selectedPosition = if (isCyclic) {
            normalizePosition(_selectedPosition + targetPosition)
        } else {
            (_selectedPosition + targetPosition).coerceIn(0, itemCount - 1)
        }
        _currentPosition = _selectedPosition
        scrollOffset = 0f
        targetPosition = 0
        isFlinging = false
        setScrollState(SCROLL_STATE_IDLE)
        fireItemSelected()
        invalidate()
    }

    private fun handleClick(event: MouseEvent) {
        if (itemCount == 0 || itemHeight <= 0f) return
        val contentTop = layoutParams.paddingTop
        val contentBottom = height - layoutParams.paddingBottom
        val centerY = (contentTop + contentBottom) / 2f
        val indexOffset = round((event.y - getAbsoluteY() - centerY - scrollOffset) / itemHeight).toInt()
        if (indexOffset != 0) scrollByItems(indexOffset)
    }

    private fun clampDragOffset() {
        if (isCyclic || itemCount == 0) return
        val minOffset = -(itemCount - 1 - _selectedPosition) * itemHeight
        val maxOffset = _selectedPosition * itemHeight
        scrollOffset = Mth.clamp(scrollOffset, minOffset, maxOffset)
    }

    private fun clampSelection() {
        if (itemCount == 0) {
            applyPosition(0)
            return
        }

        val clampedSelection = if (isCyclic) {
            normalizePosition(_selectedPosition)
        } else {
            _selectedPosition.coerceIn(0, itemCount - 1)
        }
        if (clampedSelection != _selectedPosition) {
            applyPosition(clampedSelection)
            return
        }

        if (!isCyclic) {
            val clampedTarget = (_selectedPosition + targetPosition)
                .coerceIn(0, itemCount - 1) - _selectedPosition
            if (clampedTarget != targetPosition) {
                targetPosition = clampedTarget
                invalidate()
            }
        }
    }

    private fun applyPosition(position: Int) {
        val target = if (isCyclic) normalizePosition(position) else position.coerceIn(0, max(0, itemCount - 1))
        if (_selectedPosition != target || scrollOffset != 0f || targetPosition != 0) {
            _selectedPosition = target
            _currentPosition = target
            scrollOffset = 0f
            targetPosition = 0
            isFlinging = false
            setScrollState(SCROLL_STATE_IDLE)
            fireItemSelected()
            invalidate()
        }
    }

    private fun fireItemSelected() {
        val pos = if (isCyclic) normalizePosition(_selectedPosition) else _selectedPosition
        val item = if (itemCount > 0) items.getOrNull(pos) else null
        onItemSelectedListener?.onItemSelected(this, item, pos)
    }

    private fun normalizePosition(position: Int): Int {
        val n = itemCount
        if (n == 0) return 0
        return ((position % n) + n) % n
    }

    private fun setScrollState(state: Int) {
        if (scrollState != state) {
            scrollState = state
            onWheelScrollListener?.onScrollStateChanged(state)
        }
    }

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SCROLLING = 2

        const val DEFAULT_VISIBLE_ITEM_COUNT = 3
        const val DEFAULT_ITEM_SPACE = 2.0f

        private const val SETTLE_EPSILON_PX = 1e-3f

        private const val MIN_VELOCITY_DT_SEC = 0.001

        private const val VELOCITY_SMOOTHING = 0.82

        private const val MAX_FLING_VELOCITY = 6000f

        private const val FLING_VELOCITY_THRESHOLD = 320f

        private const val FLING_MOMENTUM_FACTOR = 0.35f
    }
}
