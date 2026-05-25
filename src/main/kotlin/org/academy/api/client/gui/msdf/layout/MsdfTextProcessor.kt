package org.academy.api.client.gui.msdf.layout

import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.msdf.atlas.MsdfGlyph
import org.academy.api.client.gui.msdf.core.Constants
import org.academy.api.client.gui.msdf.font.MsdfFont
import org.academy.api.client.gui.msdf.font.MsdfFontService
import org.academy.api.client.gui.msdf.font.MsdfKerningManager

object MsdfTextProcessor {
    fun layout(text: String, fontSize: Float): MutableList<GlyphInstance> {
        val lines = ArrayList<LineInfo>()
        var currentLine = LineInfo()

        var i = 0
        while (i < text.length) {
            val c = text.codePointAt(i)
            if (c == '\n'.code) {
                lines.add(currentLine)
                currentLine = LineInfo()
                i++
                continue
            }

            val font = MsdfFontService.getFont(c)
            val glyph = font.getGlyph(c) ?: run { i += Character.charCount(c); continue }

            val metrics = font.metrics
            val unitsPerEM = metrics.unitsPerEm
            if (unitsPerEM.toInt() == 0) { i += Character.charCount(c); continue }
            val fontUnitScale = fontSize / unitsPerEM

            val ascender = metrics.ascender * fontUnitScale
            val lineHeight = metrics.lineHeight * fontUnitScale

            if (ascender > currentLine.maxAscender) currentLine.maxAscender = ascender
            if (lineHeight > currentLine.maxLineHeight) currentLine.maxLineHeight = lineHeight

            currentLine.characters.add(CharInfo(c, font, glyph))
            i += Character.charCount(c)
        }
        if (currentLine.characters.isNotEmpty()) lines.add(currentLine)

        val rawInstances = ArrayList<GlyphInstance>()
        var yOffset = 0f
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (line in lines) {
            val baselineY = yOffset + line.maxAscender + Constants.DEFAULT_PX_RANGE.toFloat()
            var currentX = 0f
            var prevCode = 0L
            var prevFontForLine: MsdfFont? = null

            for (ch in line.characters) {
                val font = ch.font
                val glyph = ch.glyph
                val metrics = font.metrics
                val unitsPerEM = metrics.unitsPerEm
                if (unitsPerEM.toInt() == 0) continue
                val fontUnitScale = fontSize / unitsPerEM

                if (prevCode != 0L && prevFontForLine == font) {
                    currentX += MsdfKerningManager.getKerning(
                        font.face,
                        prevCode,
                        ch.codePoint.toLong()
                    ) * fontUnitScale
                }

                val page = glyph.page
                if (page != null) {
                    val quadLeft = currentX + (glyph.bearingX * fontUnitScale)
                    val quadTop = baselineY - (glyph.bearingY * fontUnitScale)
                    val quadWidth = (glyph.planeRight - glyph.planeLeft).toFloat() * fontUnitScale
                    val quadHeight = (glyph.planeTop - glyph.planeBottom).toFloat() * fontUnitScale
                    val quadBottom = quadTop + quadHeight

                    if (quadTop < minY) minY = quadTop
                    if (quadBottom > maxY) maxY = quadBottom

                    rawInstances.add(
                        GlyphInstance(
                            page.textureView,
                            quadLeft, quadTop,
                            quadWidth, quadHeight,
                            glyph.u0, glyph.v0, glyph.u1, glyph.v1
                        )
                    )
                }

                currentX += glyph.advance * fontUnitScale
                prevCode = ch.codePoint.toLong()
                prevFontForLine = font
            }
            yOffset += line.maxLineHeight
        }

        val finalInstances = ArrayList<GlyphInstance>(rawInstances.size)
        val yShift = -minY
        for (inst in rawInstances) {
            finalInstances.add(
                GlyphInstance(
                    inst.textureView,
                    inst.x, inst.y + yShift,
                    inst.quadWidth, inst.quadHeight,
                    inst.u0, inst.v0, inst.u1, inst.v1
                )
            )
        }
        return finalInstances
    }

    private class LineInfo {
        var maxAscender: Float = 0f
        var maxLineHeight: Float = 0f
        var characters: MutableList<CharInfo> = ArrayList()
    }

    private class CharInfo(var codePoint: Int, var font: MsdfFont, var glyph: MsdfGlyph)

    data class GlyphInstance(
        val textureView: GpuTextureView,
        val x: Float, val y: Float,
        val quadWidth: Float, val quadHeight: Float,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float
    )
}