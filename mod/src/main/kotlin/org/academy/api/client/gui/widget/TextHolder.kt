package org.academy.api.client.gui.widget

import net.minecraft.util.ARGB
import kotlin.math.roundToInt

/**
 * 具备文本与配色的控件共同外观（[TextWidget]、[TextInputWidget]），对齐 Android
 * `TextView`/`EditText` 的共性 API。
 *
 * 供序列化 `bind_text` 与跨类型配色辅助方法使用，使二者在解耦后仍有统一入口。
 */
interface TextHolder : Widget {
    /** 文本内容（对标 `setText`/`getText`）。 */
    var text: String

    /** 文本字号，sp（对标 `setTextSize`，替代旧 `baseFontSize`）。 */
    var textSize: Float

    /** 文本颜色，ARGB（对标 `setTextColor`/`getTextColor`）。 */
    var textColor: Int
}

/** 便捷：以 0..1 浮点分量设色（等价于打包成 ARGB 后赋给 [TextHolder.textColor]）。 */
fun TextHolder.rgb(red: Float, green: Float, blue: Float): TextHolder {
    textColor = ARGB.color(
        255,
        (red * 255f).roundToInt().coerceIn(0, 255),
        (green * 255f).roundToInt().coerceIn(0, 255),
        (blue * 255f).roundToInt().coerceIn(0, 255)
    )
    return this
}