package org.academy.api.client.gui.text

import net.minecraft.resources.Identifier

/**
 * 文本 shaping 的不可变参数（仅影响布局/度量，与渲染技术无关）。
 *
 * 由 [TextWidget] 的 Android 式属性派生，[TextShaper] 消费；默认值即当前框架的既有行为。
 */
data class TextShapingOptions(
    /** 字母间距，em 倍数（对标 Android `letterSpacing`）。 */
    val letterSpacing: Float = 0f,
    /** 水平缩放（字距压缩/拉伸），1.0 正常（对标 Android `textScaleX`）。 */
    val textScaleX: Float = 1f,
    /** 行距倍数（对标 Android `lineSpacingMultiplier`）。 */
    val lineSpacingMultiplier: Float = 1f,
    /** 额外行距，sp（对标 Android `lineSpacingExtra`）。 */
    val lineSpacingExtra: Float = 0f,
    /** 是否把字体的 leading 计入行高（对标 Android `includeFontPadding`）。 */
    val includeFontPadding: Boolean = true,
    /** 首选字体；null 表示逐字回退解析（对标 Android `setTypeface`）。 */
    val preferredFont: Identifier? = null,
    /** 字重/字形（对标 Android `Typeface` style）。 */
    val textStyle: TextStyle = TextStyle.NORMAL
) {
    companion object {
        val DEFAULT: TextShapingOptions = TextShapingOptions()
    }
}