package org.academy.api.client.gui.unit

/**
 * 四边内缩/外缩，供 padding（[org.academy.api.client.gui.widget.Widget] 自身属性）
 * 与 margin（[org.academy.api.client.gui.widget.WidgetContainer.LayoutParams] 父级参数）共用。
 */
data class Insets(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp
) {
    val horizontal: Dp get() = left + right
    val vertical: Dp get() = top + bottom

    companion object {
        val ZERO: Insets = Insets(Dp.ZERO, Dp.ZERO, Dp.ZERO, Dp.ZERO)

        fun all(value: Dp): Insets = Insets(value, value, value, value)

        fun horizontal(horizontal: Dp, vertical: Dp = Dp.ZERO): Insets =
            Insets(horizontal, vertical, horizontal, vertical)

        fun only(
            left: Dp = Dp.ZERO,
            top: Dp = Dp.ZERO,
            right: Dp = Dp.ZERO,
            bottom: Dp = Dp.ZERO
        ): Insets = Insets(left, top, right, bottom)

        fun startEnd(start: Dp, end: Dp, top: Dp, bottom: Dp, rtl: Boolean): Insets =
            if (rtl) Insets(end, top, start, bottom) else Insets(start, top, end, bottom)
    }
}
