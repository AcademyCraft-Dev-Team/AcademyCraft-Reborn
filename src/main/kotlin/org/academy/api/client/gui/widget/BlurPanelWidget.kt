package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.render.BlurRegion
import org.academy.api.client.gui.render.RenderContext

/**
 * 磨砂玻璃面板喵.
 *
 * 渲染时登记一个屏幕空间 [BlurRegion], 由 [org.academy.api.client.gui.render.UiCompositor]
 * 在其 drawOrder 处把下方 UI 内容 (如 main) 高斯模糊后烘入. 面板本身默认透明,
 * 可通过 [background] 设置半透明着色形成毛玻璃质感. 设置 [onClick] 后面板变为可点击
 * (可兼作全屏点击关闭层).
 *
 * 限制: 仅支持轴对齐 (不缩放/旋转) 的面板.
 */
open class BlurPanelWidget(initialRadius: Float = 8f) : AbstractWidget() {
    var blurRadius: Float = initialRadius
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 设置后面板可点击, 按下时触发 (可兼作全屏点击关闭层). */
    var onClick: (() -> Unit)? = null
        set(value) {
            field = value
            isClickable = value != null
        }

    override fun onMousePressed(event: MouseEvent) {
        if (onClick != null && isMouseOver(event.x, event.y)) {
            // 主动 consume, 拦截点击不透传到下方控件 (如 cover 打开时的技能树节点).
            event.consume()
            onClick?.invoke()
            return
        }
        super.onMousePressed(event)
    }

    override fun render(context: RenderContext) {
        if (!isVisible()) return
        val x = getAbsoluteX() + getAbsoluteTranslationX()
        val y = getAbsoluteY() + getAbsoluteTranslationY()
        if (width > 0f && height > 0f) {
            context.registerBlurRegion(
                BlurRegion(x, y, width, height, blurRadius, context.drawOrder().peek())
            )
        }
        renderInternal(context)
    }
}
