package org.academy.api.client.gui.text.shape

import org.academy.api.client.gui.text.font.AwtFaceCache
import org.academy.api.client.gui.text.font.FontLineMetrics
import org.academy.api.client.gui.text.font.FontRepository
import org.academy.api.client.gui.text.model.*
import java.awt.Font
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.text.AttributedString
import java.text.Bidi

object TextShaper {
    private const val HUGE = 1e9f

    fun shape(text: String, fontSize: Float, options: TextShapingOptions = TextShapingOptions.DEFAULT): TextBlob {
        if (text.isEmpty()) return TextBlob(text, emptyList(), emptyList(), 0f, 0f)

        val lines = ArrayList<TextLine>()
        val runs = ArrayList<GlyphRun>()
        var top = 0f
        var maxRight = 0f
        var charBase = 0
        var firstLine = true
        var lastLineIndex = -1
        var lastLineBottom = 0f

        for (paragraph in text.split('\n')) {
            if (paragraph.isEmpty()) {
                val flm = defaultFontLineMetrics(fontSize)
                val above = if (firstLine && options.includeFontPadding) flm.top else flm.ascent
                val below = flm.descent
                lines.add(TextLine(charBase, charBase, top + above, above, below, flm.leading))
                lastLineIndex = lines.size - 1
                lastLineBottom = flm.bottom
                top += lineAdvance(above, below, options)
                firstLine = false
                charBase += 1
                continue
            }

            val attributed = buildAttributed(paragraph, fontSize, options)
            val measurer = LineBreakMeasurer(attributed.iterator, AwtFaceCache.renderContext)
            while (measurer.position < paragraph.length) {
                val start = measurer.position
                measurer.nextLayout(HUGE) ?: break
                val end = measurer.position
                if (end <= start) break

                var lineTop = 0f
                var lineAscent = 0f
                var lineDescent = 0f
                var lineBottom = 0f
                var lineLeading = 0f
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

                    val flm = FontRepository.getFont(fontId).lineMetrics(fontSize)
                    if (flm.top > lineTop) lineTop = flm.top
                    if (flm.ascent > lineAscent) lineAscent = flm.ascent
                    if (flm.descent > lineDescent) lineDescent = flm.descent
                    if (flm.bottom > lineBottom) lineBottom = flm.bottom
                    if (flm.leading > lineLeading) lineLeading = flm.leading

                    val awtFont = AwtFaceCache.font(fontId, fontSize, options.fontStyle.awtStyle())
                    if (awtFont != null) {
                        val chars = paragraph.substring(i, j).toCharArray()
                        val flags = if (isRtl(chars)) Font.LAYOUT_RIGHT_TO_LEFT else Font.LAYOUT_LEFT_TO_RIGHT
                        val gv = awtFont.layoutGlyphVector(AwtFaceCache.renderContext, chars, 0, chars.size, flags)
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
                val above = if (firstLine && options.includeFontPadding) lineTop else lineAscent
                val below = lineDescent
                lines.add(TextLine(charBase + start, charBase + end, top + above, above, below, lineLeading))
                lastLineIndex = lines.size - 1
                lastLineBottom = lineBottom
                top += lineAdvance(above, below, options)
                firstLine = false
            }
            charBase += paragraph.length + 1
        }

        if (options.includeFontPadding && lastLineIndex >= 0) {
            val last = lines[lastLineIndex]
            if (lastLineBottom > last.descent) {
                lines[lastLineIndex] =
                    TextLine(last.charStart, last.charEnd, last.baselineY, last.ascent, lastLineBottom, last.leading)
                top += lastLineBottom - last.descent
            }
        }

        return TextBlob(text, runs, lines, maxRight, top)
    }

    fun wrap(
        text: String,
        fontSize: Float,
        maxWidth: Float,
        options: TextShapingOptions = TextShapingOptions.DEFAULT
    ): String {
        if (text.isEmpty() || maxWidth <= 0f || !maxWidth.isFinite()) return text
        val out = StringBuilder(text.length + 8)
        var firstParagraph = true
        for (paragraph in text.split('\n')) {
            if (!firstParagraph) out.append('\n')
            firstParagraph = false
            if (paragraph.isEmpty()) continue

            val attributed = buildAttributed(paragraph, fontSize, options)
            val measurer = LineBreakMeasurer(attributed.iterator, AwtFaceCache.renderContext)
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
            AwtFaceCache.font(fontId, fontSize, options.fontStyle.awtStyle())?.let {
                attributed.addAttribute(TextAttribute.FONT, it, i, j)
            }
            i = j
        }
        return attributed
    }

    private fun resolveFontId(cp: Int, options: TextShapingOptions): net.minecraft.resources.Identifier {
        val preferred = options.preferredFont
        if (preferred != null && FontRepository.getFont(preferred).hasGlyph(cp)) return preferred
        return FontRepository.getFont(cp).descriptor.identifier
    }

    private fun lineAdvance(above: Float, below: Float, options: TextShapingOptions): Float =
        (above + below) * options.lineSpacingMultiplier + options.lineSpacingExtra

    internal fun defaultFontLineMetrics(fontSize: Float): FontLineMetrics =
        FontRepository.getFont(FontRepository.DEFAULT_FONT_ID).lineMetrics(fontSize)

    internal fun defaultLineMetrics(fontSize: Float): java.awt.font.LineMetrics? {
        val font = AwtFaceCache.font(FontRepository.DEFAULT_FONT_ID, fontSize) ?: return null
        return font.getLineMetrics(" ", AwtFaceCache.renderContext)
    }

    private fun fallbackAdvance(text: String, fontSize: Float): Float {
        val font = AwtFaceCache.font(FontRepository.DEFAULT_FONT_ID, fontSize) ?: return 0f
        return font.getStringBounds(text, AwtFaceCache.renderContext).width.toFloat()
    }

    private fun isRtl(chars: CharArray): Boolean = !Bidi(String(chars), 0).baseIsLeftToRight()

    private fun FontStyle.awtStyle(): Int = when (this) {
        FontStyle.NORMAL -> Font.PLAIN
        FontStyle.BOLD -> Font.BOLD
        FontStyle.ITALIC -> Font.ITALIC
        FontStyle.BOLD_ITALIC -> Font.BOLD or Font.ITALIC
    }
}
