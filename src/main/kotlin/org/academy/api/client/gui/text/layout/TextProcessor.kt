package org.academy.api.client.gui.text.layout

import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.glyph.Constants
import org.academy.api.client.gui.glyph.bitmap.BitmapStrikeCache
import org.academy.api.client.gui.text.MarqueeState
import org.academy.api.client.gui.text.TextBlob
import org.academy.api.client.gui.text.font.MsdfFontService
import org.academy.api.client.gui.text.subrun.SubRunControl
import org.academy.api.client.gui.text.subrun.TextKind
import kotlin.math.roundToLong

object TextProcessor {
    class DeviceGlyph(
        val kind: TextKind,
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
        originXGui: Float,
        originYGui: Float,
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

        val rasterPx = BitmapStrikeCache.quantize(fontSize * deviceScale * maxOf(guiScale, 0f))
        val ctmScale = if (contentScale > 0f && deviceScale > 0f) deviceScale / contentScale else 1f
        val unitDevice = contentScale * ctmScale * guiScale
        val originPhysX = originXGui * guiScale
        val originPhysY = originYGui * guiScale
        val subpixelSteps = (1 shl Constants.BITMAP_SUBPIXEL_BITS).toDouble()
        val subpixelMask = (1L shl Constants.BITMAP_SUBPIXEL_BITS) - 1L
        val subpixelInv = 1.0 / subpixelSteps
        val out = ArrayList<DeviceGlyph>()

        for (run in blob.runs) {
            val font = MsdfFontService.getFont(run.fontId)
            val unitsPerEm = font.metrics.unitsPerEm.toInt()
            if (unitsPerEm == 0) continue
            val fontUnitScale = fontSize / unitsPerEm
            val runBitmap = decideBitmap(run.glyphIndices, run.charIndices, font, rasterPx, revealCodeUnits)

            if (runBitmap && rasterPx > SubRunControl.BITMAP_PX_THRESHOLD) {
                for (g in run.glyphIndices.indices) {
                    val charIndex = run.charIndices[g]
                    if (charIndex >= revealCodeUnits) continue
                    if (!font.isGlyphReadyByIndex(run.glyphIndices[g])) {
                        font.getGlyphByIndex(run.glyphIndices[g], codePointAt(blob.text, charIndex))
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
                    val penPhysX = originPhysX + penX * unitDevice
                    val qx = (penPhysX * subpixelSteps).roundToLong()
                    val phaseX = (qx and subpixelMask).toInt()
                    val basePhysX = (qx - phaseX) * subpixelInv

                    val penPhysY = originPhysY + baselineY * unitDevice
                    val qy = (penPhysY * subpixelSteps).roundToLong()
                    val phaseY = (qy and subpixelMask).toInt()
                    val basePhysY = (qy - phaseY) * subpixelInv

                    val bg = BitmapStrikeCache.getGlyph(font, glyphIndex, rasterPx, phaseX, phaseY)
                        ?: continue
                    val unit = fontSize / rasterPx
                    val x = ((((basePhysX + bg.bearingLeft) / guiScale) - originXGui) / ctmScale).toFloat()
                    val y = ((((basePhysY - bg.bearingTop) / guiScale) - originYGui) / ctmScale).toFloat()
                    val w = bg.widthPx * unit * contentScale
                    out.add(
                        DeviceGlyph(
                            TextKind.BITMAP, bg.page.textureView,
                            x, y, w, bg.heightPx * unit * contentScale,
                            bg.u0, bg.v0, bg.u1, bg.v1,
                            MarqueeState.fadeFactor(
                                x,
                                fadeLeftEdge,
                                fadeWidth,
                                fadeLen,
                                fadeLeftStrength,
                                fadeRightStrength
                            ),
                            MarqueeState.fadeFactor(
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
                    val mg = font.getGlyphByIndex(glyphIndex, codePointAt(blob.text, charIndex)) ?: continue
                    val x = (penX + mg.planeLeft * fontUnitScale) * contentScale
                    val y = (baselineY - mg.planeTop * fontUnitScale) * contentScale
                    val w = (mg.planeRight - mg.planeLeft) * fontUnitScale * contentScale
                    out.add(
                        DeviceGlyph(
                            TextKind.MSDF, mg.page.textureView,
                            x, y, w,
                            (mg.planeTop - mg.planeBottom) * fontUnitScale * contentScale,
                            mg.u0, mg.v0, mg.u1, mg.v1,
                            MarqueeState.fadeFactor(
                                x,
                                fadeLeftEdge,
                                fadeWidth,
                                fadeLen,
                                fadeLeftStrength,
                                fadeRightStrength
                            ),
                            MarqueeState.fadeFactor(
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
        font: org.academy.api.client.gui.text.font.MsdfFont,
        rasterPx: Float,
        revealCodeUnits: Int
    ): Boolean {
        if (rasterPx <= SubRunControl.BITMAP_PX_THRESHOLD) return true
        var allReady = true
        for (g in glyphIndices.indices) {
            if (charIndices[g] >= revealCodeUnits) continue
            if (!font.isGlyphReadyByIndex(glyphIndices[g])) {
                allReady = false
                break
            }
        }
        return !allReady
    }

    private fun lineFor(blob: TextBlob, charIndex: Int): org.academy.api.client.gui.text.TextLine? {
        for (line in blob.lines) {
            if (charIndex >= line.charStart && charIndex < line.charEnd) return line
        }
        return blob.lines.lastOrNull()
    }

    private fun codePointAt(text: String, charIndex: Int): Int =
        if (charIndex in text.indices) text.codePointAt(charIndex) else 0
}
