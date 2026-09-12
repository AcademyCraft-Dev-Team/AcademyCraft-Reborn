package org.academy.api.client.gui.widget

import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.render.ScissorRect
import kotlin.math.max

/**
 * 固定字号 + 缓速跑马灯标签：文本不随宽度缩小，超出控件范围时缓慢往返滚动，
 * 两端停留片刻，适配长歌名/歌手名的展示喵。
 */
open class MarqueeLabelWidget(text: String) : LabelWidget(text) {
    /**
     * 滚动速度（GUI 单位/秒）。
     */
    var scrollSpeed: Float = 5f
        set(value) {
            field = max(0.5f, value)
        }

    /**
     * 滚动到两端后的停留时间（毫秒）。
     */
    var holdMillis: Long = 1500L
        set(value) {
            field = max(0L, value)
        }

    private var scrollOffset = 0f
    private var scrollingForward = true
    private var holdUntilMillis = 0L
    private var lastFrameMillis = 0L

    override fun calculateLayoutScale(
        baseTextWidth: Float,
        baseTextHeight: Float,
        constraintWidth: Float,
        constraintHeight: Float
    ): Float = 1f

    override val textScrollOffsetX: Float
        get() = scrollOffset

    override fun render(context: RenderContext) {
        updateScrollOffset()
        super.render(context)
    }

    override fun beginTextClip(context: RenderContext) {
        // 用当前位姿矩阵取世界坐标: getAbsoluteTranslationX 不含滚动面板 render 时的平移,
        // 滚动后布局坐标会与实际绘制位置错位, 导致与面板裁剪的交集为空反而失去裁剪喵.
        val matrix = context.pose().last().pose()
        val poseScaleX = kotlin.math.sqrt(matrix.m00() * matrix.m00() + matrix.m10() * matrix.m10())
        val poseScaleY = kotlin.math.sqrt(matrix.m01() * matrix.m01() + matrix.m11() * matrix.m11())
        context.enableScissor(
            ScissorRect(
                matrix.m30(),
                matrix.m31(),
                width * poseScaleX,
                height * poseScaleY
            )
        )
    }

    override fun endTextClip(context: RenderContext) {
        context.disableScissor()
    }

    private fun updateScrollOffset() {
        val now = System.currentTimeMillis()
        val deltaMillis = if (lastFrameMillis == 0L) 0L else now - lastFrameMillis
        lastFrameMillis = now

        val lp = layoutParams
        val availableWidth = width - lp.paddingLeft - lp.paddingRight
        val visualTextWidth = getTextWidth(text) * scale
        if (visualTextWidth <= availableWidth || availableWidth <= 0f) {
            if (scrollOffset != 0f) {
                scrollOffset = 0f
                invalidate()
            }
            return
        }
        if (now < holdUntilMillis) return

        val maxOffset = visualTextWidth - availableWidth
        val delta = scrollSpeed * (deltaMillis / 1000f)
        scrollOffset = if (scrollingForward) {
            scrollOffset + delta
        } else {
            scrollOffset - delta
        }
        if (scrollOffset >= maxOffset) {
            scrollOffset = maxOffset
            scrollingForward = false
            holdUntilMillis = now + holdMillis
        } else if (scrollOffset <= 0f) {
            scrollOffset = 0f
            scrollingForward = true
            holdUntilMillis = now + holdMillis
        }
        invalidate()
    }
}
