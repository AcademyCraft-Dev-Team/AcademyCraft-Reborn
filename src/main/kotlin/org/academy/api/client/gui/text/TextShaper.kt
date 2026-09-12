package org.academy.api.client.gui.text

import org.academy.api.client.gui.text.font.MsdfFontService
import java.awt.Font
import java.awt.font.LineBreakMeasurer
import java.text.AttributedString
import java.text.Bidi
import java.awt.font.TextAttribute

/**
 * AWT 单一布局源（对标 Skia shaping）：`java.awt.font` 负责 shaping/advance/kerning/换行/行度量，
 * 产出设备无关的 [TextBlob]（glyph index + AWT user units 位置）。
 *
 * 光栅化不在此处；字形标识用字体 glyph index，与 FreeType 编号一致，因此布局与光栅一致。
 * [TextShapingOptions] 里的字母间距/水平缩放/行距在 user units 层面应用，与渲染技术无关。
 */
object TextShaper {
    private const val HUGE = 1e9f

    /** 无换行 shaping（文本可含 `\n`）。 */
    fun shape(text: String, fontSize: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): TextBlob {
        if (text.isEmpty()) return TextBlob(text, emptyList(), emptyList(), emptyList(), 0f, 0f)

        val lines = ArrayList<TextLine>()
        val runs = ArrayList<GlyphRun>()
        val layouts = ArrayList<java.awt.font.TextLayout?>()
        var top = 0f
        var maxRight = 0f
        var charBase = 0

        for (paragraph in text.split('\n')) {
            if (paragraph.isEmpty()) {
                val lm = defaultLineMetrics(fontSize)
                val asc = lm?.ascent ?: fontSize
                val desc = lm?.descent ?: 0f
                val lead = lm?.leading ?: 0f
                lines.add(TextLine(charBase, charBase, top + asc, asc, desc, lead))
                layouts.add(null)
                top += lineAdvance(asc, desc, lead, options)
                charBase += 1
                continue
            }

            val attributed = buildAttributed(paragraph, fontSize, options)
            val measurer = LineBreakMeasurer(attributed.iterator, AwtFontCache.renderContext)
            while (measurer.position < paragraph.length) {
                val start = measurer.position
                val layout = measurer.nextLayout(HUGE) ?: break
                val end = measurer.position
                if (end <= start) break

                val asc = layout.ascent
                val desc = layout.descent
                val lead = layout.leading
                val baseline = top + asc
                var x = 0f
                var i = start

                while (i < end) {
                    val cp = paragraph.codePointAt(i)
                    val fontId = resolveFontId(cp, options)
                    var j = i + Character.charCount(cp)
                    while (j < end) {
                        val cp2 = paragraph.codePointAt(j)
                        if (resolveFontId(cp2, options) != fontId) break
                        j += Character.charCount(cp2)
                    }

                    val awtFont = AwtFontCache.font(fontId, fontSize, options.textStyle.awtStyle())
                    if (awtFont != null) {
                        val chars = paragraph.substring(i, j).toCharArray()
                        val flags = if (isRtl(chars)) Font.LAYOUT_RIGHT_TO_LEFT else Font.LAYOUT_LEFT_TO_RIGHT
                        val gv = awtFont.layoutGlyphVector(AwtFontCache.renderContext, chars, 0, chars.size, flags)
                        val n = gv.numGlyphs
                        val indices = IntArray(n)
                        val positions = FloatArray(n)
                        val advances = FloatArray(n)
                        val charIndices = IntArray(n)
                        val ls = options.letterSpacing * fontSize
                        val baseX = gv.getGlyphPosition(0).x.toFloat()
                        for (g in 0 until n) {
                            indices[g] = gv.getGlyphCode(g)
                            val pos = gv.getGlyphPosition(g).x.toFloat() - baseX
                            positions[g] = x + pos * options.textScaleX + ls * g
                            val endPos =
                                (if (g + 1 < n) gv.getGlyphPosition(g + 1).x.toFloat() else gv.getGlyphPosition(n).x.toFloat()) - baseX
                            advances[g] = (endPos - pos) * options.textScaleX + ls
                            charIndices[g] = charBase + i + gv.getGlyphCharIndex(g)
                        }
                        runs.add(GlyphRun(fontId, indices, positions, advances, charIndices))
                        x += (gv.getGlyphPosition(n).x.toFloat() - baseX) * options.textScaleX + ls * n
                    } else {
                        x += fallbackAdvance(paragraph.substring(i, j), fontSize)
                    }
                    i = j
                }

                if (x > maxRight) maxRight = x
                lines.add(TextLine(charBase + start, charBase + end, baseline, asc, desc, lead))
                layouts.add(layout)
                top += lineAdvance(asc, desc, lead, options)
            }
            charBase += paragraph.length + 1
        }

        return TextBlob(text, runs, lines, layouts, maxRight, top)
    }

    /** AWT 换行，返回插入 `\n` 后的字符串。 */
    fun wrap(text: String, fontSize: Float, maxWidth: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): String {
        if (text.isEmpty() || maxWidth <= 0f || !maxWidth.isFinite()) return text
        val out = StringBuilder(text.length + 8)
        var firstParagraph = true
        for (paragraph in text.split('\n')) {
            if (!firstParagraph) out.append('\n')
            firstParagraph = false
            if (paragraph.isEmpty()) continue

            val attributed = buildAttributed(paragraph, fontSize, options)
            val measurer = LineBreakMeasurer(attributed.iterator, AwtFontCache.renderContext)
            var last = 0
            while (measurer.position < paragraph.length) {
                val start = measurer.position
                measurer.nextLayout(maxWidth) ?: break
                val end = measurer.position
                if (end <= start) break
                if (last > 0) out.append('\n')
                out.append(paragraph, start, end)
                last = end
            }
        }
        return out.toString()
    }

    private fun buildAttributed(text: String, fontSize: Float, options: TextShapingOptions): AttributedString {
        val attributed = AttributedString(text)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val fontId = resolveFontId(cp, options)
            var j = i + Character.charCount(cp)
            while (j < text.length) {
                val cp2 = text.codePointAt(j)
                if (resolveFontId(cp2, options) != fontId) break
                j += Character.charCount(cp2)
            }
            AwtFontCache.font(fontId, fontSize, options.textStyle.awtStyle())?.let {
                attributed.addAttribute(TextAttribute.FONT, it, i, j)
            }
            i = j
        }
        return attributed
    }

    /** 首选字体（若覆盖该码点）优先，否则走逐字回退解析。 */
    private fun resolveFontId(cp: Int, options: TextShapingOptions): net.minecraft.resources.Identifier {
        val preferred = options.preferredFont
        if (preferred != null && MsdfFontService.getFont(preferred).hasGlyph(cp)) return preferred
        return MsdfFontService.getFont(cp).descriptor.identifier
    }

    private fun lineAdvance(asc: Float, desc: Float, lead: Float, options: TextShapingOptions): Float {
        val base = asc + desc + (if (options.includeFontPadding) lead else 0f)
        return base * options.lineSpacingMultiplier + options.lineSpacingExtra
    }

    /** 默认字体的行度量；空文本用于占位行高，使 caret 与首行对齐。 */
    internal fun defaultLineMetrics(fontSize: Float): java.awt.font.LineMetrics? {
        val font = AwtFontCache.font(MsdfFontService.DEFAULT_FONT_ID, fontSize) ?: return null
        return font.getLineMetrics(" ", AwtFontCache.renderContext)
    }

    private fun fallbackAdvance(text: String, fontSize: Float): Float {
        val font = AwtFontCache.font(MsdfFontService.DEFAULT_FONT_ID, fontSize) ?: return 0f
        return font.getStringBounds(text, AwtFontCache.renderContext).width.toFloat()
    }

    private fun isRtl(chars: CharArray): Boolean = !Bidi(String(chars), 0).baseIsLeftToRight()

    private fun TextStyle.awtStyle(): Int = when (this) {
        TextStyle.NORMAL -> Font.PLAIN
        TextStyle.BOLD -> Font.BOLD
        TextStyle.ITALIC -> Font.ITALIC
        TextStyle.BOLD_ITALIC -> Font.BOLD or Font.ITALIC
    }
}