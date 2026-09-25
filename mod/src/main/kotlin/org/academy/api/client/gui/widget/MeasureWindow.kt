package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import kotlin.math.max

internal data class MeasureWindow(
    val hasWidth: Boolean,
    val hasHeight: Boolean,
    val availableWidth: Float,
    val availableHeight: Float
)

internal fun Widget.measureWindow(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec): MeasureWindow {
    val lp = layoutParams
    return MeasureWindow(
        hasWidth = widthMeasureSpec.mode != MeasureSpec.Mode.UNSPECIFIED,
        hasHeight = heightMeasureSpec.mode != MeasureSpec.Mode.UNSPECIFIED,
        availableWidth = max(0f, widthMeasureSpec.size - lp.paddingLeft - lp.paddingRight),
        availableHeight = max(0f, heightMeasureSpec.size - lp.paddingTop - lp.paddingBottom)
    )
}
