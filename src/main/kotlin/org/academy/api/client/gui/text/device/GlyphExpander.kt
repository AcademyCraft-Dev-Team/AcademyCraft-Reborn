package org.academy.api.client.gui.text.device

import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.text.font.FontRepository
import org.academy.api.client.gui.text.glyph.RasterPathPolicy
import org.academy.api.client.gui.text.glyph.RasterSpec
import org.academy.api.client.gui.text.glyph.bitmap.GlyphRasterizer
import org.academy.api.client.gui.text.glyph.bitmap.GlyphStrikeCache
import org.academy.api.client.gui.text.glyph.msdf.MsdfGlyphCache
import org.academy.api.client.gui.text.model.GlyphKind
import org.academy.api.client.gui.text.model.TextBlob
import org.academy.api.client.gui.text.model.TextLine
import kotlin.math.roundToLong

object GlyphExpander {
    class DeviceGlyph(
        val kind: GlyphKind,
        val textureView: GpuTextureView,
        val x: Float, val y: Float, val width: Float, val height: Float,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val fadeLeft: Float = 1f, val fadeRight: Float = 1f
    )

    fun layout(
        blob: TextBlob,
        fontSize: Float,
        contentScale: Float,
        deviceScale: Float,
        guiScale: Float,
        revealCodeUnits: Int,
        fadeViewportLeft: Float = 0f,
        fadeViewportWidth: Float = 0f,
        fadeLength: Float = 0f,
        fadeLeftStrength: Float = 0f,
        fadeRightStrength: Float = 0f
    ): List<DeviceGlyph> {
        if (blob.runs.isEmpty()) return emptyList()

        val fadeLeftEdge = fadeViewportLeft * contentScale
        val fadeWidth = fadeViewportWidth * contentScale
        val fadeLen = fadeLength * contentScale

        val rasterPx = GlyphRasterizer.quantize(fontSize * deviceScale * maxOf(guiScale, 0f))
        val ctmScale = if (contentScale > 0f && deviceScale > 0f) deviceScale / contentScale else 1f
        val unitDevice = contentScale * ctmScale * guiScale
        val subpixelSteps = (1 shl RasterSpec.BITMAP_SUBPIXEL_BITS).toDouble()
        val subpixelMask = (1L shl RasterSpec.BITMAP_SUBPIXEL_BITS) - 1L
        val subpixelInv = 1.0 / subpixelSteps
        val out = ArrayList<DeviceGlyph>()

        for (run in blob.runs) {
            val font = FontRepository.getFont(run.fontId)
            val unitsPerEm = font.metrics.unitsPerEm.toInt()
            if (unitsPerEm == 0) continue
            val fontUnitScale = fontSize / unitsPerEm
            val msdf = MsdfGlyphCache.of(font)
            val runBitmap = decideBitmap(run.glyphIndices, run.charIndices, msdf, rasterPx, revealCodeUnits)

            if (runBitmap && rasterPx > RasterPathPolicy.BITMAP_PX_THRESHOLD) {
                for (g in run.glyphIndices.indices) {
                    val charIndex = run.charIndices[g]
                    if (charIndex >= revealCodeUnits) continue
                    if (!msdf.isReady(run.glyphIndices[g])) {
                        msdf.getGlyphByIndex(run.glyphIndices[g], codePointAt(blob.text, charIndex))
                    }
                }
            }

            for (g in run.glyphIndices.indices) {
                val charIndex = run.charIndices[g]
                if (charIndex >= revealCodeUnits) continue
                val line = lineFor(blob, charIndex) ?: continue
                val penX = run.positionsX[g]
                val baselineY = line.baselineY
                val glyphIndex = run.glyphIndices[g]

                if (runBitmap) {
                    if (rasterPx < 1f) continue
                    val penPhysX = penX * unitDevice
                    val qx = (penPhysX * subpixelSteps).roundToLong()
                    val phaseX = (qx and subpixelMask).toInt()
                    val basePhysX = (qx - phaseX) * subpixelInv

                    val penPhysY = baselineY * unitDevice
                    val qy = (penPhysY * subpixelSteps).roundToLong()
                    val phaseY = (qy and subpixelMask).toInt()
                    val basePhysY = (qy - phaseY) * subpixelInv

                    val bg = GlyphStrikeCache.getGlyph(font, glyphIndex, rasterPx, phaseX, phaseY)
                        ?: continue
                    val unit = fontSize / rasterPx
                    val x = (((basePhysX + bg.bearingLeft) / guiScale) / ctmScale).toFloat()
                    val y = (((basePhysY - bg.bearingTop) / guiScale) / ctmScale).toFloat()
                    val w = bg.widthPx * unit * contentScale
                    out.add(
                        DeviceGlyph(
                            GlyphKind.BITMAP, bg.page.textureView,
                            x, y, w, bg.heightPx * unit * contentScale,
                            bg.u0, bg.v0, bg.u1, bg.v1,
                            FadeMask.fadeFactor(
                                x,
                                fadeLeftEdge,
                                fadeWidth,
                                fadeLen,
                                fadeLeftStrength,
                                fadeRightStrength
                            ),
                            FadeMask.fadeFactor(
                                x + w,
                                fadeLeftEdge,
                                fadeWidth,
                                fadeLen,
                                fadeLeftStrength,
                                fadeRightStrength
                            )
                        )
                    )
                } else {
                    val mg = msdf.getGlyphByIndex(glyphIndex, codePointAt(blob.text, charIndex)) ?: continue
                    val x = (penX + mg.planeLeft * fontUnitScale) * contentScale
                    val y = (baselineY - mg.planeTop * fontUnitScale) * contentScale
                    val w = (mg.planeRight - mg.planeLeft) * fontUnitScale * contentScale
                    out.add(
                        DeviceGlyph(
                            GlyphKind.MSDF, mg.page.textureView,
                            x, y, w,
                            (mg.planeTop - mg.planeBottom) * fontUnitScale * contentScale,
                            mg.u0, mg.v0, mg.u1, mg.v1,
                            FadeMask.fadeFactor(
                                x,
                                fadeLeftEdge,
                                fadeWidth,
                                fadeLen,
                                fadeLeftStrength,
                                fadeRightStrength
                            ),
                            FadeMask.fadeFactor(
                                x + w,
                                fadeLeftEdge,
                                fadeWidth,
                                fadeLen,
                                fadeLeftStrength,
                                fadeRightStrength
                            )
                        )
                    )
                }
            }
        }
        return out
    }

    private fun decideBitmap(
        glyphIndices: IntArray,
        charIndices: IntArray,
        msdf: MsdfGlyphCache,
        rasterPx: Float,
        revealCodeUnits: Int
    ): Boolean {
        if (rasterPx <= RasterPathPolicy.BITMAP_PX_THRESHOLD) return true
        var allReady = true
        for (g in glyphIndices.indices) {
            if (charIndices[g] >= revealCodeUnits) continue
            if (!msdf.isReady(glyphIndices[g])) {
                allReady = false
                break
            }
        }
        return !allReady
    }

    private fun lineFor(blob: TextBlob, charIndex: Int): TextLine? {
        for (line in blob.lines) {
            if (charIndex >= line.charStart && charIndex < line.charEnd) return line
        }
        return blob.lines.lastOrNull()
    }

    private fun codePointAt(text: String, charIndex: Int): Int =
        if (charIndex in text.indices) text.codePointAt(charIndex) else 0
}
