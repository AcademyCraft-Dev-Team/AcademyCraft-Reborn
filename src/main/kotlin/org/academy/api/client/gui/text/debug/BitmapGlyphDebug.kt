package org.academy.api.client.gui.text.debug

import com.mojang.blaze3d.platform.NativeImage
import lovely.cane.jmsdfgen.ImportFont
import org.academy.api.client.gui.text.font.FontFace
import org.academy.api.client.gui.text.glyph.bitmap.GlyphRasterizer
import java.nio.file.Files
import java.nio.file.Path

object BitmapGlyphDebug {
    fun dump(font: FontFace, codepoint: Int, rasterPx: Float, outputDir: Path): String? {
        val quantized = GlyphRasterizer.quantize(rasterPx)
        val glyphIndex = ImportFont.getGlyphIndex(font.fontHandle, codepoint.toLong()).index()
        val raw = GlyphRasterizer.rasterRaw(font, glyphIndex, quantized, 0, 0) ?: return null

        return try {
            NativeImage(NativeImage.Format.RGBA, raw.width, raw.height, false).use { image ->
                val pixels = raw.pixels
                for (row in 0 until raw.height) {
                    for (col in 0 until raw.width) {
                        val gray = pixels[row * raw.width + col].toInt() and 0xFF
                        image.setPixelABGR(col, row, (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray)
                    }
                }
                Files.createDirectories(outputDir)
                val fileName = "glyph_U+" + Integer.toHexString(codepoint) + "_" + quantized + ".png"
                image.writeToFile(outputDir.resolve(fileName))
                "U+" + Integer.toHexString(codepoint) + " -> " + fileName +
                        " " + raw.width + "x" + raw.height +
                        " bearing(" + raw.bearingLeft + "," + raw.bearingTop + ")"
            }
        } catch (e: Exception) {
            "U+" + Integer.toHexString(codepoint) + " dump failed: " + e
        }
    }
}
