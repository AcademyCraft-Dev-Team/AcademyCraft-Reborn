package org.academy.api.client.gui.text

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.unit.Sp

/**
 * 行内样式区间，对标 Android `android.text.style.*`。
 *
 * 区间用 UTF-16 code unit 偏移（与 Android 一致，[start] 含、[end] 不含）。
 * 文本分 run 时，用这些 span 切出同 styling 的区间。
 */
sealed interface Span {
    val start: Int
    val end: Int
}

data class ForegroundColorSpan(override val start: Int, override val end: Int, val color: Int) : Span

data class BackgroundColorSpan(override val start: Int, override val end: Int, val color: Int) : Span

/** 相对字号缩放，1.0 为继承外层。 */
data class RelativeSizeSpan(override val start: Int, override val end: Int, val proportion: Float) : Span

/** 绝对字号（sp）。 */
data class AbsoluteSizeSpan(override val start: Int, override val end: Int, val size: Sp) : Span

/** 字体覆盖。 */
data class TypefaceSpan(override val start: Int, override val end: Int, val font: Identifier) : Span

/** 字重/字形覆盖。 */
data class StyleSpan(override val start: Int, override val end: Int, val style: TextStyle) : Span

/** 字距覆盖（em 倍数）。 */
data class LetterSpacingSpan(override val start: Int, override val end: Int, val letterSpacing: Float) : Span

/**
 * 可带行内样式的文本。`text` 为纯文本，`spans` 为附加样式。
 * 无 span 时等价于普通字符串，走单 run 快路径。
 */
class Spanned(
    val text: String,
    val spans: List<Span> = emptyList()
) : CharSequence by text {
    val hasSpans: Boolean get() = spans.isNotEmpty()

    override fun toString(): String = text

    override fun equals(other: Any?): Boolean =
        other is Spanned && other.text == text && other.spans == spans

    override fun hashCode(): Int = 31 * text.hashCode() + spans.hashCode()

    companion object {
        val EMPTY: Spanned = Spanned("")

        fun of(text: String): Spanned = Spanned(text)

        fun of(text: String, vararg spans: Span): Spanned = Spanned(text, spans.toList())
    }
}

/** 把任意 [CharSequence] 归一为 [Spanned]；纯文本不分配 span 列表。 */
fun CharSequence.toSpanned(): Spanned = when (this) {
    is Spanned -> this
    else -> Spanned(this.toString())
}
